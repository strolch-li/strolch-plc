package li.strolch.plc.core;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchComponent;
import li.strolch.model.Locator;
import li.strolch.model.Resource;
import li.strolch.model.log.LogMessage;
import li.strolch.model.parameter.Parameter;
import li.strolch.model.parameter.StringParameter;
import li.strolch.model.visitor.SetParameterValueVisitor;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.*;
import li.strolch.plc.core.hw.gpio.PlcGpioController;
import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.configuration.ComponentConfiguration;
import li.strolch.utils.collections.MapOfMaps;
import li.strolch.utils.dbc.DBC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.utils.helper.ExceptionHelper.getCallerMethod;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.formatNanoDuration;

public class DefaultPlcHandler extends StrolchComponent implements PlcHandler, PlcConnectionStateChangeListener {

	public static final int SILENT_THRESHOLD = 100;
	private static final int MAX_MESSAGE_QUEUE = 200;

	private PrivilegeContext ctx;
	private String plcId;
	private Plc plc;
	private PlcState plcState;
	private String plcStateMsg;
	private MapOfMaps<String, String, PlcAddress> plcAddresses;
	private MapOfMaps<String, String, PlcAddress> plcTelegrams;
	private Map<PlcAddress, String> addressesToResourceId;

	private GlobalPlcListener globalListener;

	private LinkedBlockingDeque<Runnable> updateStateQueue;
	private LinkedBlockingDeque<Consumer<GlobalPlcListener>> messageQueue;

	private boolean run;
	private Future<?> messageSenderTask;
	private Future<?> updateStateTask;

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

		this.messageQueue = new LinkedBlockingDeque<>();
		this.updateStateQueue = new LinkedBlockingDeque<>();

		super.initialize(configuration);
	}

	@Override
	public void start() throws Exception {
		this.ctx = getContainer().getPrivilegeHandler().openAgentSystemUserContext();

		this.run = true;
		this.messageSenderTask = getSingleThreadExecutor("LogSender").submit(this::sendMessages);
		this.updateStateTask = getSingleThreadExecutor("UpdateState").submit(this::updateStates);

		if (reconfigurePlc())
			startPlc();
		super.start();
	}

	@Override
	public void stop() throws Exception {
		stopPlc();

		this.run = false;
		if (this.messageSenderTask != null)
			this.messageSenderTask.cancel(true);
		if (this.updateStateTask != null)
			this.updateStateTask.cancel(true);

		if (this.ctx != null)
			getContainer().getPrivilegeHandler().invalidate(this.ctx.getCertificate());

		super.stop();
	}

	@Override
	public void destroy() throws Exception {
		if (PlcGpioController.isLoaded()) {
			logger.info("Destroying GPIO Controller...");
			PlcGpioController.getInstance().shutdown();
		}
		super.destroy();
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

			logger.info("Reconfigured PLC with {} addresses", this.plcAddresses.size());
			return true;

		} catch (Exception e) {
			logger.error("Failed to configure Plc", e);
			this.plcState = PlcState.Failed;
			this.plcStateMsg = "Configure failed: " + getExceptionMessageWithCauses(e);

			return false;
		}
	}

	private Plc configure(PrivilegeContext ctx, MapOfMaps<String, String, PlcAddress> plcAddresses,
			MapOfMaps<String, String, PlcAddress> plcTelegrams, Map<PlcAddress, String> addressesToResourceId)
			throws Exception {

		Plc plc;
		try (StrolchTransaction tx = openTx(ctx.getCertificate(), getCallerMethod(), true)) {

			String plcClassName = getConfiguration().getString("plcClass", DefaultPlc.class.getName());

			plc = PlcConfigurator.configurePlc(tx, plcClassName, plcAddresses, plcTelegrams, addressesToResourceId);
			plc.setConnectionStateChangeListener(this);
			plcAddresses.values().forEach(plcAddress -> plc.register(plcAddress, this::queueUpdateState));

			if (tx.getConfiguration().hasParameter(PARAM_VERBOSE)) {
				boolean verboseOverride = tx.getConfiguration().getBoolean(PARAM_VERBOSE);
				logger.info("Overriding XML verbose property from configuration resource to {}", verboseOverride);
				this.verbose = verboseOverride;
			}

			if (tx.needsCommit())
				tx.commitOnClose();
		}

		return plc;
	}

	private PrivilegeContext validateCtx() {
		if (this.ctx == null) {
			this.ctx = getContainer().getPrivilegeHandler().openAgentSystemUserContext();
		} else {
			try {
				getContainer().getPrivilegeHandler().validateSystemSession(this.ctx);
			} catch (Exception e) {
				logger.error("PrivilegeContext for session {} is not valid, reopening.",
						this.ctx.getCertificate().getSessionId(), e);
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
			logger.warn("No PlcAddress exists for {}-{}", resource, action);
		} else {
			this.plc.unregister(plcAddress, listener);
		}
	}

	private void queueUpdateState(PlcAddress plcAddress, Object o) {
		this.updateStateQueue.add(() -> updatePlcAddress(plcAddress, o));
	}

	private void queueUpdateState(PlcConnection connection) {
		this.updateStateQueue.add(
				() -> updateConnectionState(connection.getId(), connection.getState(), connection.getStateMsg()));
	}

	@Override
	public void sendMsg(LogMessage message) {
		addMsg(listener -> listener.sendMsg(message));
	}

	@Override
	public void disableMsg(Locator locator) {
		addMsg(listener -> listener.disableMsg(locator));
	}

	private synchronized void addMsg(Consumer<GlobalPlcListener> consumer) {
		if (this.messageQueue.size() > MAX_MESSAGE_QUEUE)
			this.messageQueue.removeFirst();
		this.messageQueue.addLast(consumer);
	}

	private void sendMessages() {
		while (this.run) {
			try {

				while (this.globalListener == null) {
					Thread.sleep(100L);
				}

				this.messageQueue.takeFirst().accept(this.globalListener);
			} catch (InterruptedException e) {
				logger.warn("Interrupted");
			} catch (Exception e) {
				logger.error("Failed to send message", e);
			}
		}
	}

	private void updateStates() {
		logger.info("Update State Handler running...");
		while (this.run) {
			try {
				this.updateStateQueue.take().run();
			} catch (InterruptedException e) {
				logger.error("Interrupted!");
			} catch (Exception e) {
				logger.error("Failed to perform state update", e);
			}
		}
		logger.info("Update State Handler stopped.");
	}

	private void updatePlcAddress(PlcAddress address, Object value) {
		long s = 0L;
		if (this.verbose)
			s = nanoTime();

		String addressId = this.addressesToResourceId.get(address);
		if (addressId == null) {
			logger.error("No PlcAddress mapping for {}", address);
			return;
		}

		Certificate cert = validateCtx().getCertificate();
		try (StrolchTransaction tx = openTx(cert, getCallerMethod(), false).silentThreshold(SILENT_THRESHOLD,
				MILLISECONDS)) {
			tx.lock(Resource.locatorFor(TYPE_PLC_ADDRESS, addressId));

			Resource addressRes = tx.getResourceBy(TYPE_PLC_ADDRESS, addressId, true);
			Parameter<?> valueP = addressRes.getParameter(PARAM_VALUE, true);
			if (valueP.getValue().equals(value)) {
				if (this.verbose)
					logger.info("Ignoring PlcAddress {} unchanged value {}", address.toKey(), value);
				return;
			}

			if (this.verbose)
				logger.info("PlcAddress {} has changed from {} to {}", address.toKey(), valueP.getValue(), value);

			valueP.accept(new SetParameterValueVisitor(value));
			tx.update(addressRes);
			tx.commitOnClose();
		} catch (Exception e) {
			logger.error("Failed to update PlcAddress {} with new value {}", addressId, value, e);
		}

		if (this.verbose && (nanoTime() - s > MILLISECONDS.toNanos(SILENT_THRESHOLD)))
			logger.info("async update {} took {}", address.toKey(), formatNanoDuration(nanoTime() - s));
	}

	private void updateConnectionState(String id, ConnectionState state, String stateMsg) {
		long s = 0L;
		if (this.verbose)
			s = nanoTime();

		try (StrolchTransaction tx = openTx(validateCtx().getCertificate(), getCallerMethod(), false).silentThreshold(
				SILENT_THRESHOLD, MILLISECONDS)) {
			tx.lock(Resource.locatorFor(TYPE_PLC_CONNECTION, id));
			Resource connection = tx.getResourceBy(TYPE_PLC_CONNECTION, id);

			StringParameter stateP = connection.getParameter(BAG_PARAMETERS, PARAM_STATE, true);
			StringParameter stateMsgP = connection.getParameter(BAG_PARAMETERS, PARAM_STATE_MSG, true);

			logger.info("State for PlcConnection {} has changed from {} to {}", connection.getId(), stateP.getValue(),
					state.name());
			stateP.setValue(state.name());
			stateMsgP.setValue(stateMsg);
			tx.update(connection);

			tx.commitOnClose();
		} catch (Exception e) {
			logger.error("Failed to update state for connection {}", id, e);
		}

		if (this.verbose)
			logger.info("updateConnectionState took {}", formatNanoDuration(nanoTime() - s));
	}

	@Override
	public void send(String resource, String action) {
		send(resource, action, true, true);
	}

	@Override
	public void send(String resource, String action, Object value) {
		send(resource, action, value, true, true);
	}

	@Override
	public void send(String resource, String action, boolean catchExceptions, boolean notifyGlobalListener) {
		PlcAddress plcAddress = this.plcTelegrams.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcTelegram exists for " + resource + "-" + action);

		if (plcAddress.defaultValue == null)
			throw new IllegalStateException("Can not send PlcAddress as no default value set for " + plcAddress);

		this.plc.send(plcAddress, catchExceptions, notifyGlobalListener);
	}

	@Override
	public void send(String resource, String action, Object value, boolean catchExceptions,
			boolean notifyGlobalListener) {
		PlcAddress plcAddress = this.plcTelegrams.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcTelegram exists for " + resource + "-" + action);

		this.plc.send(plcAddress, value, catchExceptions, notifyGlobalListener);
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
		queueUpdateState(connection);
	}
}
