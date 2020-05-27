package li.strolch.plc.core;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.utils.helper.ExceptionHelper.getCallerMethod;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.formatNanoDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchComponent;
import li.strolch.model.log.LogMessage;
import li.strolch.model.Locator;
import li.strolch.model.Resource;
import li.strolch.model.StrolchValueType;
import li.strolch.model.parameter.Parameter;
import li.strolch.model.parameter.StringParameter;
import li.strolch.model.visitor.SetParameterValueVisitor;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.*;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.configuration.ComponentConfiguration;
import li.strolch.utils.collections.MapOfMaps;
import li.strolch.utils.dbc.DBC;

public class DefaultPlcHandler extends StrolchComponent implements PlcHandler, PlcConnectionStateChangeListener {

	public static final int SILENT_THRESHOLD = 60;
	private PrivilegeContext ctx;
	private String plcId;
	private Plc plc;
	private PlcState plcState;
	private String plcStateMsg;
	private MapOfMaps<String, String, PlcAddress> plcAddresses;
	private MapOfMaps<String, String, PlcAddress> plcTelegrams;
	private Map<PlcAddress, String> addressesToResourceId;

	private GlobalPlcListener globalListener;

	private LinkedBlockingDeque<MessageTask> messageQueue;
	private int maxMessageQueue;
	private boolean run;
	private Future<?> messageSenderTask;

	private boolean verbose;

	public DefaultPlcHandler(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	public ComponentContainer getContainer() {
		return super.getContainer();
	}

	@Override
	public String getPlcId() {
		return this.plcId;
	}

	@Override
	public Plc getPlc() {
		return this.plc;
	}

	@Override
	public PlcState getPlcState() {
		return this.plcState;
	}

	@Override
	public String getPlcStateMsg() {
		return this.plcStateMsg;
	}

	@Override
	public PlcAddress getPlcAddress(String resource, String action) {
		DBC.PRE.assertNotNull("resource must not be null", resource);
		DBC.PRE.assertNotEmpty("action must not be empty", action);
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("PlcAddress for " + resource + "-" + action + " does not exist!");
		return plcAddress;
	}

	@Override
	public String getPlcAddressId(String resource, String action) {
		DBC.PRE.assertNotNull("resource must not be null", resource);
		DBC.PRE.assertNotEmpty("action must not be empty", action);
		PlcAddress plcAddress = getPlcAddress(resource, action);
		String addressId = this.addressesToResourceId.get(plcAddress);
		if (addressId == null)
			throw new IllegalStateException(
					"PlcAddress mapping ID for " + resource + "-" + action + " does not exist!");
		return addressId;
	}

	@Override
	public void initialize(ComponentConfiguration configuration) throws Exception {

		this.plcId = configuration.getString("plcId", null);

		// validate Plc class name
		String plcClassName = configuration.getString("plcClass", DefaultPlc.class.getName());
		Class.forName(plcClassName);

		this.plcState = PlcState.Initial;
		this.plcStateMsg = PlcState.Initial.name();
		this.plcAddresses = new MapOfMaps<>();
		this.plcTelegrams = new MapOfMaps<>();
		this.addressesToResourceId = new HashMap<>();
		this.verbose = configuration.getBoolean("verbose", false);

		this.maxMessageQueue = configuration.getInt("maxMessageQueue", 100);
		this.messageQueue = new LinkedBlockingDeque<>();

		super.initialize(configuration);
	}

	@Override
	public void start() throws Exception {
		this.ctx = getContainer().getPrivilegeHandler().openAgentSystemUserContext();

		this.run = true;
		this.messageSenderTask = getExecutorService("LogSender").submit(this::sendMessages);

		if (reconfigurePlc())
			startPlc();
		super.start();
	}

	@Override
	public void stop() throws Exception {
		stopPlc();
		if (this.ctx != null)
			getContainer().getPrivilegeHandler().invalidate(this.ctx.getCertificate());

		this.run = false;
		this.messageSenderTask.cancel(false);

		super.stop();
	}

	@Override
	public void startPlc() {
		if (this.plc == null)
			throw new IllegalStateException("Can not start as not yet configured!");

		this.plc.start();
		this.plcState = PlcState.Started;
		this.plcStateMsg = PlcState.Started.name();
		logger.info("Started PLC");
	}

	@Override
	public void stopPlc() {
		if (this.plc != null)
			this.plc.stop();
		this.plcState = PlcState.Stopped;
		this.plcStateMsg = PlcState.Stopped.name();
		logger.info("Stopped PLC");
	}

	@Override
	public boolean reconfigurePlc() {
		if (this.plcState == PlcState.Started)
			throw new IllegalStateException("Can not reconfigure if started!");

		try {
			MapOfMaps<String, String, PlcAddress> plcAddresses = new MapOfMaps<>();
			MapOfMaps<String, String, PlcAddress> plcTelegrams = new MapOfMaps<>();
			Map<PlcAddress, String> addressesToResourceId = new HashMap<>();
			this.plc = configure(validateCtx(), plcAddresses, plcTelegrams, addressesToResourceId);
			this.plc.setVerbose(this.verbose);
			this.plcAddresses = plcAddresses;
			this.plcTelegrams = plcTelegrams;
			this.addressesToResourceId = addressesToResourceId;

			this.plcState = PlcState.Configured;
			this.plcStateMsg = PlcState.Configured.name();

			if (this.globalListener != null)
				this.plc.setGlobalListener(this.globalListener);

			logger.info("Reconfigured PLC with " + this.plcAddresses.size() + " addresses");
			return true;

		} catch (Exception e) {
			logger.error("Failed to configure Plc", e);
			this.plcState = PlcState.Failed;
			this.plcStateMsg = "Configure failed: " + getExceptionMessageWithCauses(e);

			return false;
		}
	}

	private void updateConnectionState(StrolchTransaction tx, Resource connection, PlcConnection plcConnection) {
		StringParameter stateP = connection.getParameter(BAG_PARAMETERS, PARAM_STATE, true);
		StringParameter stateMsgP = connection.getParameter(BAG_PARAMETERS, PARAM_STATE_MSG, true);

		logger.info("State for PlcConnection {} has changed from {} to {}", connection.getId(), stateP.getValue(),
				plcConnection.getState().name());

		stateP.setValue(plcConnection.getState().name());
		stateMsgP.setValue(plcConnection.getStateMsg());
		tx.update(connection);
	}

	private Plc configure(PrivilegeContext ctx, MapOfMaps<String, String, PlcAddress> plcAddresses,
			MapOfMaps<String, String, PlcAddress> plcTelegrams, Map<PlcAddress, String> addressesToResourceId)
			throws Exception {

		Plc plc;
		try (StrolchTransaction tx = openTx(ctx.getCertificate(), getCallerMethod(), true)) {

			String plcClassName = getConfiguration().getString("plcClass", DefaultPlc.class.getName());

			plc = PlcConfigurator.configurePlc(tx, plcClassName, plcAddresses, plcTelegrams, addressesToResourceId);
			plc.setConnectionStateChangeListener(this);
			plcAddresses.values().stream().filter(a -> a.type == PlcAddressType.Notification)
					.forEach(plcAddress -> plc.register(plcAddress, this::asyncUpdateState));

			if (tx.needsCommit())
				tx.commitOnClose();
		}

		return plc;
	}

	private void asyncUpdateState(PlcAddress address, Object value) {
		getExecutorService("PlcAddressUpdater").submit(() -> updatePlcAddress(address, value));
	}

	private void asyncUpdateState(PlcConnection connection) {
		getExecutorService("PlcConnectionUpdater").submit(() -> updateConnectionState(connection));
	}

	private void updatePlcAddress(PlcAddress address, Object value) {
		long s = 0L;
		if (this.verbose)
			s = nanoTime();

		String addressId = this.addressesToResourceId.get(address);
		if (addressId == null) {
			logger.error("No PlcAddress mapping for " + address);
			return;
		}

		try (StrolchTransaction tx = openTx(validateCtx().getCertificate(), getCallerMethod(), false)
				.silentThreshold(SILENT_THRESHOLD, MILLISECONDS)) {
			tx.lock(Resource.locatorFor(TYPE_PLC_ADDRESS, addressId));
			Resource addressRes = tx.getResourceBy(TYPE_PLC_ADDRESS, addressId, true);

			// see if we need to invert a boolean flag
			if (address.valueType == StrolchValueType.BOOLEAN && address.inverted)
				value = !((boolean) value);

			Parameter<?> valueP = addressRes.getParameter(PARAM_VALUE, true);
			if (this.verbose)
				logger.info("PlcAddress {}-{} has changed from {} to {}", address.resource, address.action,
						valueP.getValue(), value);

			valueP.accept(new SetParameterValueVisitor(value));
			tx.update(addressRes);
			tx.commitOnClose();
		} catch (Exception e) {
			logger.error("Failed to update PlcAddress " + addressId + " with new value " + value, e);
		}

		if (this.verbose)
			logger.info("async update " + address.address + " took " + (formatNanoDuration(nanoTime() - s)));
	}

	private void updateConnectionState(PlcConnection plcConnection) {
		long s = 0L;
		if (this.verbose)
			s = nanoTime();

		try (StrolchTransaction tx = openTx(validateCtx().getCertificate(), getCallerMethod(), false)
				.silentThreshold(SILENT_THRESHOLD, MILLISECONDS)) {
			tx.lock(Resource.locatorFor(TYPE_PLC_CONNECTION, plcConnection.getId()));
			Resource connection = tx.getResourceBy(TYPE_PLC_CONNECTION, plcConnection.getId());
			updateConnectionState(tx, connection, plcConnection);
			tx.update(connection);
			tx.commitOnClose();
		} catch (Exception e) {
			logger.error("Failed to update state for connection " + plcConnection.getId(), e);
		}

		if (this.verbose)
			logger.info("updateConnectionState took " + (formatNanoDuration(nanoTime() - s)));
	}

	private PrivilegeContext validateCtx() {
		if (this.ctx == null) {
			this.ctx = getContainer().getPrivilegeHandler().openAgentSystemUserContext();
		} else {
			try {
				getContainer().getPrivilegeHandler().validateSystemSession(this.ctx);
			} catch (Exception e) {
				logger.error("PrivilegeContext for session " + this.ctx.getCertificate().getSessionId()
						+ " is not valid, reopening.", e);
				this.ctx = getContainer().getPrivilegeHandler().openAgentSystemUserContext();
			}
		}
		return this.ctx;
	}

	@Override
	public void setGlobalListener(GlobalPlcListener listener) {
		this.globalListener = listener;
		if (this.plc != null)
			this.plc.setGlobalListener(listener);
	}

	@Override
	public void register(String resource, String action, PlcListener listener) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcAddress exists for " + resource + "-" + action);
		this.plc.register(plcAddress, listener);
	}

	@Override
	public void unregister(String resource, String action, PlcListener listener) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null) {
			logger.warn("No PlcAddress exists for " + resource + "-" + action);
		} else {
			this.plc.unregister(plcAddress, listener);
		}
	}

	@Override
	public void sendMsg(LogMessage message) {
		addMsg(new LogMessageTask(message));
	}

	@Override
	public void disableMsg(Locator locator) {
		addMsg(new DisableMessageTask(locator));
	}

	private synchronized void addMsg(MessageTask task) {
		if (this.messageQueue.size() > this.maxMessageQueue)
			this.messageQueue.removeFirst();
		this.messageQueue.addLast(task);
	}

	private void sendMessages() {
		while (this.run) {
			try {

				if (this.globalListener == null) {
					Thread.sleep(100L);
					continue;
				}

				this.messageQueue.takeFirst().accept(this.globalListener);
			} catch (Exception e) {
				logger.error("Failed to send message", e);
			}
		}
	}

	@Override
	public void send(String resource, String action) {
		PlcAddress plcAddress = this.plcTelegrams.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcTelegram exists for " + resource + "-" + action);

		if (plcAddress.defaultValue == null)
			throw new IllegalStateException("Can not send PlcAddress as no default value set for " + plcAddress);

		this.plc.send(plcAddress);
	}

	@Override
	public void send(String resource, String action, Object value) {
		PlcAddress plcAddress = this.plcTelegrams.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcTelegram exists for " + resource + "-" + action);

		this.plc.send(plcAddress, value);
	}

	@Override
	public void notify(String resource, String action, Object value) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcAddress exists for " + resource + "-" + action);

		if (plcAddress.type != PlcAddressType.Notification)
			throw new IllegalStateException(
					"Can not notify PlcAddress " + plcAddress + " as it is not a notification!");

		this.plc.syncNotify(plcAddress.address, value);
	}

	@Override
	public StrolchTransaction openTx(Certificate cert, boolean readOnly) {
		return super.openTx(cert, readOnly);
	}

	@Override
	public void notifyStateChange(PlcConnection connection) {
		asyncUpdateState(connection);
	}

	private interface MessageTask {
		void accept(GlobalPlcListener listener);
	}

	private static class LogMessageTask implements MessageTask {
		private LogMessage message;

		private LogMessageTask(LogMessage message) {
			this.message = message;
		}

		@Override
		public void accept(GlobalPlcListener listener) {
			listener.sendMsg(message);
		}
	}

	private static class DisableMessageTask implements MessageTask {
		private Locator locator;

		private DisableMessageTask(Locator locator) {
			this.locator = locator;
		}

		@Override
		public void accept(GlobalPlcListener listener) {
			listener.disableMsg(locator);
		}
	}
}
