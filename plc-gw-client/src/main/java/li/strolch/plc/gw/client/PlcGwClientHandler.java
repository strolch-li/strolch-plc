package li.strolch.plc.gw.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.websocket.*;
import jakarta.websocket.CloseReason.CloseCodes;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchComponent;
import li.strolch.agent.api.StrolchRealm;
import li.strolch.agent.api.VersionQueryResult;
import li.strolch.model.Locator;
import li.strolch.model.Resource;
import li.strolch.model.log.LogMessage;
import li.strolch.model.parameter.StringParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.GlobalPlcListener;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.*;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.configuration.ComponentConfiguration;
import li.strolch.utils.CheckedRunnable;
import li.strolch.utils.helper.NetworkHelper;
import org.glassfish.tyrus.client.ClientManager;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.net.NetworkInterface.getByInetAddress;
import static li.strolch.model.Tags.Json.*;
import static li.strolch.plc.core.DefaultPlcHandler.SILENT_THRESHOLD;
import static li.strolch.plc.model.ModelHelper.valueToJson;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.runtime.StrolchConstants.DEFAULT_REALM;
import static li.strolch.utils.helper.ExceptionHelper.*;
import static li.strolch.utils.helper.NetworkHelper.formatMacAddress;
import static li.strolch.utils.helper.StringHelper.isEmpty;

public class PlcGwClientHandler extends StrolchComponent implements GlobalPlcListener {

	public static final String PLC = "PLC";
	public static final String SERVER_CONNECTED = "ServerConnected";
	public static final PlcAddressKey K_PLC_SERVER_CONNECTED = PlcAddressKey.keyFor(PLC, SERVER_CONNECTED);

	private static final long PING_DELAY = 90;
	private static final long RETRY_DELAY = 30;
	private static final int INITIAL_DELAY = 10;

	private boolean verbose;

	private String plcId;
	private boolean gwConnectToServer;
	private String gwUsername;
	private String gwPassword;
	private String gwServerUrl;

	private PlcHandler plcHandler;

	private ClientManager gwClient;
	private volatile Session gwSession;
	private volatile boolean authenticated;

	private ScheduledFuture<?> serverConnectFuture;

	private Map<PlcAddress, Object> notConnectedQueue;
	private LinkedBlockingDeque<CheckedRunnable> messageQueue;
	private int maxMessageQueue;
	private boolean run;
	private Future<?> messageSenderTask;

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
		this.plcId = getComponent(PlcHandler.class).getPlcId();
		this.gwConnectToServer = configuration.getBoolean("gwConnectToServer", true);
		this.gwUsername = configuration.getString("gwUsername", null);
		this.gwPassword = configuration.getSecret("gwPassword");
		this.gwServerUrl = configuration.getString("gwServerUrl", null);

		this.maxMessageQueue = configuration.getInt("maxMessageQueue", 100);
		this.messageQueue = new LinkedBlockingDeque<>();
		this.notConnectedQueue = Collections.synchronizedMap(new LinkedHashMap<>());

		super.initialize(configuration);
	}

	@Override
	public void start() throws Exception {

		this.plcHandler = getComponent(PlcHandler.class);

		if (this.plcHandler.getPlcState() == PlcState.Started)
			notifyPlcConnectionState(ConnectionState.Disconnected);
		this.plcHandler.setGlobalListener(this);

		if (this.gwConnectToServer) {
			delayConnect(INITIAL_DELAY, TimeUnit.SECONDS);
			this.run = true;
			this.messageSenderTask = getExecutorService("MessageSender").submit(this::sendMessages);
		}

		super.start();
	}

	private void notifyPlcConnectionState(ConnectionState disconnected) {
		getExecutorService("MessageSender").submit(() -> {
			try {
				getComponent(PlcHandler.class).notify(PLC, SERVER_CONNECTED, disconnected.name());
			} catch (Exception e) {
				logger.error("Failed to notify PLC of connection state", e);
			}
		});
	}

	@Override
	public void stop() throws Exception {

		this.run = false;
		this.authenticated = false;
		if (this.messageSenderTask != null)
			this.messageSenderTask.cancel(true);

		notifyPlcConnectionState(ConnectionState.Disconnected);

		if (this.gwSession != null) {
			try {
				this.gwSession.close(new CloseReason(CloseCodes.GOING_AWAY, "Shutting down"));
			} catch (Exception e) {
				logger.error("Failed to close server session: {}", e.getMessage());
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
		this.serverConnectFuture = getScheduledExecutor("ConnectionTimer").schedule(this::connectToServer, delay, unit);
	}

	private void connectToServer() {

		// connect to Server
		logger.info("Connecting to Server at {}...", this.gwServerUrl);
		try {
			this.gwClient = ClientManager.createClient();
			this.gwSession = this.gwClient.connectToServer(new PlcGwClientEndpoint(this), new URI(this.gwServerUrl));
		} catch (Exception e) {
			Throwable rootCause = getRootCause(e);
			if (rootCause instanceof InterruptedException) {
				logger.error("Interrupted while connecting. Stopping.");
				return;
			}

			if (rootCause.getMessage() != null && rootCause.getMessage().contains("Connection refused")) {
				logger.error("Connection refused to connect to server. Will try to connect again in "
						+ RETRY_DELAY
						+ "s: {}", getExceptionMessageWithCauses(e));
			} else if (rootCause.getMessage() != null && rootCause
					.getMessage()
					.contains("Response code was not 101: 404.")) {
				logger.error("Connection failed with 404 error code. Is URL {} correct?", this.gwServerUrl);
				logger.error("Server not yet ready with 404 error. Will try again in " + RETRY_DELAY + "s");
			} else {
				logger.error("Failed to connect to server! Will try to connect again in " + RETRY_DELAY + "s", e);
			}

			closeBrokenGwSessionUpdateState("Failed to connect to server",
					"Connection refused to connect to server. Will try to connect again in "
							+ RETRY_DELAY
							+ "s: "
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

		logger.info("{}: Connected to Server.", this.gwSession.getId());

		// schedule the heart beat timer
		if (this.serverConnectFuture != null)
			this.serverConnectFuture.cancel(true);
		this.serverConnectFuture = getScheduledExecutor("Server").scheduleWithFixedDelay(this::pingServer, PING_DELAY,
				PING_DELAY, TimeUnit.SECONDS);
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
		logger.info("Closing GW session: {}", msg);
		this.authenticated = false;

		if (this.serverConnectFuture != null)
			this.serverConnectFuture.cancel(true);

		if (this.gwSession != null && this.gwSession.isOpen()) {
			try {
				this.gwSession.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, msg));
			} catch (Exception e) {
				logger.error("Failed to close server session due to {}", e.getMessage());
			}
		}

		if (this.gwClient != null) {
			this.gwClient.shutdown();
		}

		this.gwClient = null;
		this.gwSession = null;
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

			logger.info("{}: Pinging Server...", this.gwSession.getId());
			this.gwSession.getBasicRemote().sendPong(ByteBuffer.wrap(this.plcId.getBytes()));

			long lastUpdate = System.currentTimeMillis() - this.lastSystemStateNotification;
			if (lastUpdate > TimeUnit.HOURS.toMillis(1)) {
				logger.info("Sending system state to server...");
				JsonObject stateJ = new JsonObject();
				stateJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_STATE_NOTIFICATION);
				stateJ.addProperty(PARAM_PLC_ID, this.plcId);
				stateJ.add(PARAM_IP_ADDRESSES, getIpAddresses());
				stateJ.add(PARAM_VERSIONS, getVersions());
				stateJ.add(PARAM_SYSTEM_STATE, getContainer().getAgent().getSystemState(1, TimeUnit.HOURS));

				sendDataToClient(stateJ);
				this.lastSystemStateNotification = System.currentTimeMillis();
			}

			return false;

		} catch (Exception e) {
			logger.error("Failed to send Ping to Server, closing server session due to: {}", getExceptionMessage(e));
			return true;
		}
	}

	private void async(CheckedRunnable runnable) {
		if (this.messageQueue.size() > this.maxMessageQueue)
			this.messageQueue.removeFirst();
		this.messageQueue.addLast(runnable);
	}

	@Override
	public void sendMsg(LogMessage message) {
		async(() -> {

			JsonObject messageJ = new JsonObject();
			messageJ.addProperty(PARAM_PLC_ID, this.plcId);
			messageJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_MESSAGE);
			messageJ.add(PARAM_MESSAGE, message.toJson());

			sendDataToClient(messageJ);
			if (this.verbose)
				logger.info("Sent msg {} to server", message.getLocator());
		});
	}

	@Override
	public void disableMsg(Locator locator) {
		async(() -> {

			JsonObject messageJ = new JsonObject();
			messageJ.addProperty(PARAM_PLC_ID, this.plcId);
			messageJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_DISABLE_MESSAGE);
			messageJ.addProperty(PARAM_REALM, DEFAULT_REALM);
			messageJ.addProperty(PARAM_LOCATOR, locator.toString());

			sendDataToClient(messageJ);
			if (this.verbose)
				logger.info("Sent disable msg {} to server", locator);
		});
	}

	private void notifyServer(PlcAddress plcAddress, Object value) {
		if (!plcAddress.remote)
			return;

		async(() -> {

			JsonObject notificationJ = new JsonObject();
			notificationJ.addProperty(PARAM_PLC_ID, this.plcId);
			notificationJ.addProperty(PARAM_MESSAGE_TYPE, MSG_TYPE_PLC_NOTIFICATION);
			notificationJ.addProperty(PARAM_RESOURCE, plcAddress.resource);
			notificationJ.addProperty(PARAM_ACTION, plcAddress.action);
			notificationJ.add(PARAM_VALUE, valueToJson(value));

			sendDataToClient(notificationJ);

			if (this.verbose)
				logger.info("Sent notification for {} to server", plcAddress.toKey());
		});
	}

	public void onWsMessage(String message) {
		JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
		if (!jsonObject.has(PARAM_MESSAGE_TYPE)) {
			logger.error("Received data has no message type!");
			return;
		}

		String messageType = jsonObject.get(PARAM_MESSAGE_TYPE).getAsString();

		try {
			runAsAgent(ctx -> {
				switch (messageType) {
					case MSG_TYPE_AUTHENTICATION -> handleAuthResponse(ctx, jsonObject);
					case MSG_TYPE_PLC_TELEGRAM -> async(() -> handleTelegram(jsonObject));
					case MSG_TYPE_PLC_GET_ADDRESS_STATE -> async(() -> handleGetAddressState(ctx, jsonObject));
					case null, default -> logger.error("Unhandled message type {}", messageType);
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to handle message of type " + messageType);
		}
	}

	private void handleGetAddressState(PrivilegeContext ctx, JsonObject telegramJ) throws Exception {
		PlcAddress plcAddress = null;
		try (StrolchTransaction tx = openTx(ctx.getCertificate(), true).silentThreshold(SILENT_THRESHOLD,
				TimeUnit.MILLISECONDS)) {
			plcAddress = parsePlcAddress(telegramJ);

			String plcAddressId = this.plcHandler.getPlcAddressId(plcAddress.resource, plcAddress.action);
			Resource address = tx.getResourceBy(TYPE_PLC_ADDRESS, plcAddressId, true);
			Object value = address.getParameter(PARAM_VALUE, true).getValue();
			telegramJ.add(PARAM_VALUE, valueToJson(value));

			telegramJ.addProperty(PARAM_STATE, PlcResponseState.Done.name());
			telegramJ.addProperty(PARAM_STATE_MSG, "");

			logger.info("Sent address state for {} = {} to server", plcAddress.toKey(), value);

		} catch (Exception e) {
			handleFailedTelegram(telegramJ, plcAddress, e);
		}

		sendDataToClient(telegramJ);
	}

	private void handleTelegram(JsonObject telegramJ) throws Exception {

		PlcAddress plcAddress = null;
		try {
			plcAddress = parsePlcAddress(telegramJ);

			if (telegramJ.has(PARAM_VALUE)) {
				String valueS = telegramJ.get(PARAM_VALUE).getAsString();
				Object value = plcAddress.valueType.parseValue(valueS);
				this.plcHandler.send(plcAddress.resource, plcAddress.action, value, false, true);
			} else {
				this.plcHandler.send(plcAddress.resource, plcAddress.action, false, true);
			}

			telegramJ.addProperty(PARAM_STATE, PlcResponseState.Done.name());
			telegramJ.addProperty(PARAM_STATE_MSG, "");

		} catch (Exception e) {
			handleFailedTelegram(telegramJ, plcAddress, e);
		}

		sendDataToClient(telegramJ);

		if (this.verbose)
			logger.info("Sent Telegram response for {} to server", plcAddress == null ? "unknown" : plcAddress.toKey());
	}

	private void handleAuthResponse(PrivilegeContext ctx, JsonObject response) {

		if (!response.has(PARAM_STATE) || !response.has(PARAM_STATE_MSG) || !response.has(PARAM_AUTH_TOKEN)) {

			closeBrokenGwSessionUpdateState(ctx, "Auth failed!", "Failed to authenticated with Server: At least one of "
					+ PARAM_STATE
					+ ", "
					+ PARAM_STATE_MSG
					+ ", "
					+ PARAM_AUTH_TOKEN
					+ " params is missing on Auth Response");

			throw new IllegalStateException("Failed to authenticated with Server: At least one of "
					+ PARAM_STATE
					+ ", "
					+ PARAM_STATE_MSG
					+ ", "
					+ PARAM_AUTH_TOKEN
					+ " params is missing on Auth Response");
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
		logger.info("{}: Successfully authenticated with Server!", this.gwSession.getId());

		saveServerConnectionState(ctx, ConnectionState.Connected, "");
		notifyPlcConnectionState(ConnectionState.Connected);
		this.authenticated = true;

		// we are connected, so flush messages
		//noinspection SynchronizeOnNonFinalField
		synchronized (this.notConnectedQueue) {
			this.notConnectedQueue.forEach(this::notifyServer);
			this.notConnectedQueue.clear();
		}
	}

	public void onWsPong(PongMessage message, Session session) {
		logger.info("{}: Received pong {}", session.getId(), message.toString());
	}

	public void onWsOpen(Session session) {
		logger.info("{}: New Session", session.getId());
	}

	public void onWsClose(Session session, CloseReason closeReason) {
		this.authenticated = false;
		logger.info("Session closed with ID {} due to {} {}. Reconnecting in " + RETRY_DELAY + "s.", session.getId(),
				closeReason.getCloseCode(), closeReason.getReasonPhrase());

		if (this.gwSession != null) {
			closeBrokenGwSessionUpdateState(closeReason.getReasonPhrase(),
					MessageFormat.format("Session closed with ID {0} due to {1} {2}. Reconnecting in {3}s.",
							session.getId(), closeReason.getCloseCode(), closeReason.getReasonPhrase(), RETRY_DELAY));
		}

		delayConnect(RETRY_DELAY, TimeUnit.SECONDS);
	}

	public void onWsError(Session session, Throwable throwable) {
		logger.error("{}: Received error: {}", session.getId(), throwable.getMessage(), throwable);
	}

	@SuppressWarnings("SynchronizeOnNonFinalField")
	private void sendDataToClient(JsonObject jsonObject) throws IOException {
		if (this.gwSession == null)
			throw new IOException("gwSession null! Not authenticated!");
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

	private void sendMessages() {
		while (this.run) {

			if (!this.authenticated) {
				try {
					Thread.sleep(100L);
				} catch (InterruptedException e) {
					logger.error("Interrupted!");
					if (!this.run)
						return;
				}

				continue;
			}

			CheckedRunnable runnable = null;
			try {
				runnable = this.messageQueue.takeFirst();
				runnable.run();
			} catch (Exception e) {
				closeBrokenGwSessionUpdateState("Failed to send message",
						"Failed to send message, reconnecting in " + RETRY_DELAY + "s.");
				if (runnable != null) {
					this.messageQueue.addFirst(runnable);
					logger.error(
							"Failed to send message, reconnecting in " + RETRY_DELAY + "s. And then retrying message.",
							e);
				}

				delayConnect(RETRY_DELAY, TimeUnit.SECONDS);
			}
		}
	}

	private void saveServerConnectionState(PrivilegeContext ctx, ConnectionState state, String stateMsg) {

		StrolchRealm realm = getContainer().getRealm(ctx.getCertificate());
		try (StrolchTransaction tx = realm
				.openTx(ctx.getCertificate(), "saveServerConnectionState", false)
				.silentThreshold(SILENT_THRESHOLD, TimeUnit.MILLISECONDS)) {
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
			this.versions.add(AGENT_VERSION, versionQueryResult.getAgentVersion().toJson(true));
			this.versions.add(APP_VERSION, versionQueryResult.getAppVersion().toJson(true));
			this.versions.add(COMPONENT_VERSIONS, versionQueryResult
					.getComponentVersions()
					.stream()
					.map(v -> v.toJson(true))
					.collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
		}

		return this.versions;
	}

	public JsonArray getIpAddresses() {
		if (this.ipAddresses == null || this.ipAddresses.isEmpty() || (
				System.currentTimeMillis() - this.ipAddressesUpdateTime > 10000L)) {
			try {
				this.ipAddresses = NetworkHelper.findInet4Addresses().stream().map(add -> {
					String mac;
					try {
						mac = formatMacAddress(getByInetAddress(add).getHardwareAddress());
					} catch (SocketException e) {
						logger.error("Failed to get HW address for {}", add.getHostAddress(), e);
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

	@Override
	public void handleNotification(PlcAddress address, Object value) {

		// if not connected, then queue notifications, storing last value per address
		if (!this.authenticated && address.plcAddressKey.equals(K_PLC_SERVER_CONNECTED)) {
			// keep this notification after other notifications
			this.notConnectedQueue.remove(address);
			this.notConnectedQueue.put(address, value);
			return;
		}

		notifyServer(address, value);
	}

	private PlcAddress parsePlcAddress(JsonObject telegramJ) {
		if (!telegramJ.has(PARAM_RESOURCE) || !telegramJ.has(PARAM_ACTION))
			throw new IllegalArgumentException("Both " + PARAM_RESOURCE + " and " + PARAM_ACTION + " is required!");

		String resource = telegramJ.get(PARAM_RESOURCE).getAsString();
		String action = telegramJ.get(PARAM_ACTION).getAsString();

		return this.plcHandler.getPlcAddress(resource, action);
	}

	private static void handleFailedTelegram(JsonObject telegramJ, PlcAddress plcAddress, Exception e) {
		if (plcAddress == null) {
			logger.error("Failed to handle telegram: {}", telegramJ, e);
			telegramJ.addProperty(PARAM_STATE, PlcResponseState.Failed.name());
			telegramJ.addProperty(PARAM_STATE_MSG,
					"Could not evaluate PlcAddress: " + getExceptionMessage(getRootCause(e), false));
		} else {
			logger.error("Failed to execute telegram: {}", plcAddress.toKeyAddress(), e);
			telegramJ.addProperty(PARAM_STATE, PlcResponseState.Failed.name());
			telegramJ.addProperty(PARAM_STATE_MSG,
					"Failed to perform " + plcAddress.toKey() + ": " + getExceptionMessage(getRootCause(e), false));
		}
	}

	@ClientEndpoint
	public static class PlcGwClientEndpoint {

		private final PlcGwClientHandler gwHandler;

		public PlcGwClientEndpoint(PlcGwClientHandler gwHandler) {
			this.gwHandler = gwHandler;
		}

		@OnMessage
		public void onMessage(String message) {
			this.gwHandler.onWsMessage(message);
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
