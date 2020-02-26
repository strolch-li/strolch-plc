package li.strolch.plc.gw.client;

import static java.net.NetworkInterface.getByInetAddress;
import static li.strolch.model.Tags.Json.*;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessage;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.NetworkHelper.formatMacAddress;
import static li.strolch.utils.helper.StringHelper.isEmpty;

import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import li.strolch.agent.api.*;
import li.strolch.model.Resource;
import li.strolch.model.parameter.StringParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcResponseState;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.configuration.ComponentConfiguration;
import li.strolch.utils.helper.ExceptionHelper;
import li.strolch.utils.helper.NetworkHelper;
import org.glassfish.tyrus.client.ClientManager;

public class PlcGwClientHandler extends StrolchComponent {

	private static final String THREAD_POOL = "PlcNotifications";

	private static final long PING_DELAY = 90;
	private static final long RETRY_DELAY = 30;
	private static final int INITIAL_DELAY = 10;

	private boolean verbose;

	private String plcId;
	private String gwUsername;
	private String gwPassword;
	private String gwServerUrl;

	private ClientManager gwClient;
	private volatile Session gwSession;
	private boolean authenticated;

	private ScheduledFuture<?> serverConnectFuture;

	private long lastSystemStateNotification;
	private long ipAddressesUpdateTime;
	private JsonArray ipAddresses;
	private JsonObject versions;

	public PlcGwClientHandler(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	public void initialize(ComponentConfiguration configuration) throws Exception {

		this.verbose = configuration.getBoolean("verbose", false);
		this.plcId = configuration.getString("plcId", null);
		this.gwUsername = configuration.getString("gwUsername", null);
		this.gwPassword = configuration.getString("gwPassword", null);
		this.gwServerUrl = configuration.getString("gwServerUrl", null);

		super.initialize(configuration);
	}

	@Override
	public void start() throws Exception {

		PlcHandler plcHandler = getComponent(PlcHandler.class);

		if (plcHandler.getPlcState() == PlcState.Started)
			notifyPlcConnectionState(ConnectionState.Disconnected);
		plcHandler.setGlobalListener(
				(address, value) -> getExecutorService(THREAD_POOL).submit(() -> notifyServer(address, value)));

		delayConnect(INITIAL_DELAY, TimeUnit.SECONDS);

		super.start();
	}

	private void notifyPlcConnectionState(ConnectionState disconnected) {
		try {
			getComponent(PlcHandler.class).notify("PLC", "ServerConnected", disconnected.name());
		} catch (Exception e) {
			logger.error("Failed to notify PLC of connection state", e);
		}
	}

	@Override
	public void stop() throws Exception {

		notifyPlcConnectionState(ConnectionState.Disconnected);

		if (this.gwSession != null) {
			try {
				this.gwSession.close(new CloseReason(CloseCodes.GOING_AWAY, "Shutting down"));
			} catch (Exception e) {
				logger.error("Failed to close server session: " + e.getMessage());
			}
		}

		if (this.gwClient != null) {
			this.gwClient.shutdown();
		}

		if (this.serverConnectFuture != null) {
			this.serverConnectFuture.cancel(true);
		}

		super.stop();
	}

	private void delayConnect(long delay, TimeUnit unit) {
		if (this.serverConnectFuture != null)
			this.serverConnectFuture.cancel(true);
		this.serverConnectFuture = getScheduledExecutor("Server").schedule(this::connectToServer, delay, unit);
	}

	private void connectToServer() {

		// connect to Server
		logger.info("Connecting to Server at " + this.gwServerUrl + "...");
		try {
			this.gwClient = ClientManager.createClient();
			this.gwSession = this.gwClient.connectToServer(new PlcGwClientEndpoint(this), new URI(this.gwServerUrl));
		} catch (Exception e) {
			Throwable rootCause = ExceptionHelper.getRootCause(e);
			if (rootCause.getMessage() != null && rootCause.getMessage().contains("Connection refused")) {
				logger.error(
						"Connection refused to connect to server. Will try to connect again in " + RETRY_DELAY + "s: "
								+ getExceptionMessageWithCauses(e));
			} else {
				logger.error("Failed to connect to server! Will try to connect again in " + RETRY_DELAY + "s", e);
			}

			closeBrokenGwSessionUpdateState("Failed to connect to server",
					"Connection refused to connect to server. Will try to connect again in " + RETRY_DELAY + "s: "
							+ getExceptionMessageWithCauses(e));
			delayConnect(RETRY_DELAY, TimeUnit.SECONDS);
			return;
		}

		// register session on server by sending a heart beat, which sends our plcId
		this.lastSystemStateNotification = System.currentTimeMillis();
		if (tryPingServer()) {
			logger.error("Failed to ping server. Will try to connect again in " + RETRY_DELAY + "s");
			closeBrokenGwSessionUpdateState("Ping failed",
					"Failed to ping server. Will try to connect again in " + RETRY_DELAY + "s");
			delayConnect(RETRY_DELAY, TimeUnit.SECONDS);
			return;
		}

		// now authenticate
		JsonObject authJ = new JsonObject();
		authJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_AUTHENTICATION);
		authJ.addProperty(PARAM_PLC_ID, this.plcId);
		authJ.addProperty(PARAM_USERNAME, this.gwUsername);
		authJ.addProperty(PARAM_PASSWORD, this.gwPassword);
		authJ.add(PARAM_IP_ADDRESSES, getIpAddresses());
		authJ.add(PARAM_VERSIONS, getVersions());
		authJ.add(PARAM_SYSTEM_STATE, getContainer().getAgent().getSystemState(1, TimeUnit.HOURS));
		try {
			sendDataToClient(authJ);
		} catch (IOException e) {
			String msg = "Failed to send Auth to server";
			logger.error(msg, e);
			closeBrokenGwSessionUpdateState(msg, msg);
			delayConnect(RETRY_DELAY, TimeUnit.SECONDS);
			return;
		}

		logger.info(this.gwSession.getId() + ": Connected to Server.");

		// schedule the heart beat timer
		if (this.serverConnectFuture != null)
			this.serverConnectFuture.cancel(true);
		this.serverConnectFuture = getScheduledExecutor("Server")
				.scheduleWithFixedDelay(this::pingServer, PING_DELAY, PING_DELAY, TimeUnit.SECONDS);
	}

	private void closeBrokenGwSessionUpdateState(String closeReason, String connectionStateMsg) {
		try {
			runAsAgent(ctx -> closeBrokenGwSessionUpdateState(ctx, closeReason, connectionStateMsg));
		} catch (Exception e) {
			logger.error("Failed to close GW Session!", e);
		}

		notifyPlcConnectionState(ConnectionState.Failed);
	}

	private void closeBrokenGwSessionUpdateState(PrivilegeContext ctx, String closeReason, String connectionStateMsg) {
		saveServerConnectionState(ctx, ConnectionState.Failed, connectionStateMsg);
		closeGwSession(closeReason);
	}

	private void closeGwSession(String msg) {

		if (this.serverConnectFuture != null)
			this.serverConnectFuture.cancel(true);

		if (this.gwSession != null) {
			try {
				this.gwSession.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, msg));
			} catch (Exception e) {
				logger.error("Failed to close server session due to " + e.getMessage());
			}
		}

		if (this.gwClient != null) {
			this.gwClient.shutdown();
		}

		this.gwClient = null;
		this.gwSession = null;
		this.authenticated = false;
	}

	private void pingServer() {
		if (tryPingServer()) {
			logger.error("Failed to ping server. Reconnecting...");
			closeBrokenGwSessionUpdateState("Ping failed",
					"Failed to ping server. Will try to connect again in " + RETRY_DELAY + "s");
			delayConnect(RETRY_DELAY, TimeUnit.MILLISECONDS);
		}
	}

	private boolean tryPingServer() {
		try {

			logger.info(this.gwSession.getId() + ": Pinging Server...");
			this.gwSession.getBasicRemote().sendPong(ByteBuffer.wrap(this.plcId.getBytes()));

			long lastUpdate = System.currentTimeMillis() - this.lastSystemStateNotification;
			if (lastUpdate > TimeUnit.HOURS.toMillis(1)) {
				logger.info("Sending system state to server...");
				JsonObject stateJ = new JsonObject();
				stateJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_AUTHENTICATION);
				stateJ.addProperty(PARAM_PLC_ID, this.plcId);
				stateJ.add(PARAM_IP_ADDRESSES, getIpAddresses());
				stateJ.add(PARAM_VERSIONS, getVersions());
				stateJ.add(PARAM_SYSTEM_STATE, getContainer().getAgent().getSystemState(1, TimeUnit.HOURS));
				sendDataToClient(stateJ);
				this.lastSystemStateNotification = System.currentTimeMillis();
			}

			return false;

		} catch (Exception e) {
			logger.error("Failed to send Ping to Server, closing server session due to: " + getExceptionMessage(e));
			return true;
		}
	}

	private void notifyServer(PlcAddress plcAddress, Object value) {
		if (!this.authenticated) {
			logger.warn("Not yet authenticated with server, ignoring update for " + plcAddress + ": " + value);
			return;
		}

		JsonObject notificationJ = new JsonObject();
		notificationJ.addProperty(PARAM_PLC_ID, this.plcId);
		notificationJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_PLC_NOTIFICATION);
		notificationJ.addProperty(PARAM_RESOURCE, plcAddress.resource);
		notificationJ.addProperty(PARAM_ACTION, plcAddress.action);

		if (value instanceof Boolean)
			notificationJ.add(PARAM_VALUE, new JsonPrimitive((Boolean) value));
		else if (value instanceof Number)
			notificationJ.add(PARAM_VALUE, new JsonPrimitive((Number) value));
		else if (value instanceof String)
			notificationJ.add(PARAM_VALUE, new JsonPrimitive((String) value));
		else
			notificationJ.add(PARAM_VALUE, new JsonPrimitive(value.toString()));

		try {
			sendDataToClient(notificationJ);
			if (this.verbose)
				logger.info("Sent notification for " + plcAddress.toKey() + " to server");
		} catch (IOException e) {
			logger.error("Failed to send notification to server", e);
		}
	}

	public void onWsMessage(Session session, String message) {
		//logger.info(session.getId() + ": Handling message");

		JsonObject jsonObject = new JsonParser().parse(message).getAsJsonObject();
		if (!jsonObject.has(PARAM_MESSAGE_TYPE)) {
			logger.error("Received data has no message type!");
			return;
		}

		String messageType = jsonObject.get(PARAM_MESSAGE_TYPE).getAsString();

		try {
			runAsAgent(ctx -> {

				if (MSG_TYPE_AUTHENTICATION.equals(messageType)) {
					handleAuthResponse(ctx, jsonObject);
				} else {
					logger.error("Unhandled message type " + messageType);
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to handle message of type " + messageType);
		}
	}

	private void handleAuthResponse(PrivilegeContext ctx, JsonObject response) {

		if (!response.has(PARAM_STATE) || !response.has(PARAM_STATE_MSG) || !response.has(PARAM_AUTH_TOKEN)) {

			closeBrokenGwSessionUpdateState(ctx, "Auth failed!",
					"Failed to authenticated with Server: At least one of " + PARAM_STATE + ", " + PARAM_STATE_MSG
							+ ", " + PARAM_AUTH_TOKEN + " params is missing on Auth Response");

			throw new IllegalStateException(
					"Failed to authenticated with Server: At least one of " + PARAM_STATE + ", " + PARAM_STATE_MSG
							+ ", " + PARAM_AUTH_TOKEN + " params is missing on Auth Response");
		}

		if (PlcResponseState.valueOf(response.get(PARAM_STATE).getAsString()) != PlcResponseState.Sent) {
			closeBrokenGwSessionUpdateState(ctx, "Failed to authenticated with server!",
					"Failed to authenticated with Server: " + response.get(PARAM_STATE_MSG).getAsString());
			throw new IllegalStateException("Auth failed to Server: " + response.get(PARAM_STATE_MSG).getAsString());
		}

		String serverAuthToken = response.get(PARAM_AUTH_TOKEN).getAsString();
		if (isEmpty(serverAuthToken)) {
			closeBrokenGwSessionUpdateState(ctx, "Missing auth token on AUTH response!",
					"Missing auth token on AUTH response!");
			throw new IllegalStateException("Missing auth token on AUTH response!");
		}
		logger.info(this.gwSession.getId() + ": Successfully authenticated with Server!");

		saveServerConnectionState(ctx, ConnectionState.Connected, "");
		notifyPlcConnectionState(ConnectionState.Connected);
		this.authenticated = true;
	}

	public void onWsPong(PongMessage message, Session session) {
		logger.info(session.getId() + ": Received pong " + message.toString());
	}

	public void onWsOpen(Session session) {
		logger.info(session.getId() + ": New Session");
	}

	public void onWsClose(Session session, CloseReason closeReason) {
		logger.info("Session closed with ID " + session.getId() + " due to " + closeReason.getCloseCode() + " "
				+ closeReason.getReasonPhrase() + ". Reconnecting in " + RETRY_DELAY + "s.");
		this.authenticated = false;

		if (this.gwSession != null) {
			closeBrokenGwSessionUpdateState(closeReason.getReasonPhrase(),
					"Session closed with ID " + session.getId() + " due to " + closeReason.getCloseCode() + " "
							+ closeReason.getReasonPhrase() + ". Reconnecting in " + RETRY_DELAY + "s.");
		}

		delayConnect(RETRY_DELAY, TimeUnit.SECONDS);
	}

	public void onWsError(Session session, Throwable throwable) {
		logger.error(session.getId() + ": Received error: " + throwable.getMessage(), throwable);
	}

	@SuppressWarnings("SynchronizeOnNonFinalField")
	private synchronized void sendDataToClient(JsonObject jsonObject) throws IOException {
		String data = jsonObject.toString();
		synchronized (this.gwSession) {
			RemoteEndpoint.Basic basic = this.gwSession.getBasicRemote();
			int pos = 0;
			while (pos + 8192 < data.length()) {
				basic.sendText(data.substring(pos, pos + 8192), false);
				pos += 8192;
			}
			basic.sendText(data.substring(pos), true);
		}
	}

	private void saveServerConnectionState(PrivilegeContext ctx, ConnectionState state, String stateMsg) {

		StrolchRealm realm = getContainer().getRealm(ctx.getCertificate());
		try (StrolchTransaction tx = realm.openTx(ctx.getCertificate(), "saveServerConnectionState", false)) {
			Resource plc = tx.getResourceBy(TYPE_PLC, this.plcId, true);

			StringParameter stateP = plc.getParameter(PARAM_CONNECTION_STATE, true);
			stateP.setValue(state.name());

			StringParameter stateMsgP = plc.getParameter(PARAM_CONNECTION_STATE_MSG, true);
			stateMsgP.setValue(stateMsg);

			tx.update(plc);
			tx.commitOnClose();
		}
	}

	private JsonObject getVersions() {
		if (this.versions == null) {
			this.versions = new JsonObject();
			VersionQueryResult versionQueryResult = getContainer().getAgent().getVersion();
			this.versions.add(AGENT_VERSION, versionQueryResult.getAgentVersion().toJson());
			this.versions.add(APP_VERSION, versionQueryResult.getAppVersion().toJson());
			this.versions.add(COMPONENT_VERSIONS,
					versionQueryResult.getComponentVersions().stream().map(ComponentVersion::toJson)
							.collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
		}

		return this.versions;
	}

	public JsonArray getIpAddresses() {
		if (this.ipAddresses == null || this.ipAddresses.size() == 0 || (
				System.currentTimeMillis() - this.ipAddressesUpdateTime > 10000L)) {
			try {
				this.ipAddresses = NetworkHelper.findInet4Addresses().stream().map(add -> {
					String mac;
					try {
						mac = formatMacAddress(getByInetAddress(add).getHardwareAddress());
					} catch (SocketException e) {
						logger.error("Failed to get HW address for " + add.getHostAddress(), e);
						mac = "(unknown)";
					}

					JsonObject j = new JsonObject();
					j.addProperty(PARAM_HOST_NAME, add.getHostName());
					j.addProperty(PARAM_IP_ADDRESS, add.getHostAddress());
					j.addProperty(PARAM_MAC_ADDRESS, mac);
					return j;
				}).collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
			} catch (SocketException e) {
				logger.error("Failed to enumerate IP Addresses!", e);
				this.ipAddresses = new JsonArray();
			}

			this.ipAddressesUpdateTime = System.currentTimeMillis();
		}
		return this.ipAddresses;
	}

	@ClientEndpoint
	public static class PlcGwClientEndpoint {

		private PlcGwClientHandler gwHandler;

		public PlcGwClientEndpoint(PlcGwClientHandler gwHandler) {
			this.gwHandler = gwHandler;
		}

		@OnMessage
		public void onMessage(String message, Session session) {
			this.gwHandler.onWsMessage(session, message);
		}

		@OnMessage
		public void onPong(PongMessage message, Session session) {
			this.gwHandler.onWsPong(message, session);
		}

		@OnOpen
		public void onOpen(Session session) {
			this.gwHandler.onWsOpen(session);
		}

		@OnClose
		public void onClose(Session session, CloseReason closeReason) {
			this.gwHandler.onWsClose(session, closeReason);
		}

		@OnError
		public void onError(Session session, Throwable throwable) {
			this.gwHandler.onWsError(session, throwable);
		}
	}
}
