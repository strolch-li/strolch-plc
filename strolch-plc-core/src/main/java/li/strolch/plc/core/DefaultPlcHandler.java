package li.strolch.plc.core;

import static li.strolch.plc.model.PlcConstants.*;
import static java.lang.System.nanoTime;
import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.formatNanoDuration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import li.strolch.plc.core.hw.*;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchComponent;
import li.strolch.model.Resource;
import li.strolch.model.parameter.Parameter;
import li.strolch.model.parameter.StringParameter;
import li.strolch.model.visitor.SetParameterValueVisitor;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.configuration.ComponentConfiguration;
import li.strolch.utils.collections.MapOfMaps;

public class DefaultPlcHandler extends StrolchComponent implements PlcHandler, PlcConnectionStateChangeListener {

	private PrivilegeContext ctx;
	private Plc plc;
	private PlcState plcState;
	private String plcStateMsg;
	private Map<PlcAddress, PlcListener> virtualListeners;
	private MapOfMaps<String, String, PlcAddress> plcAddresses;
	private MapOfMaps<String, String, PlcAddress> plcTelegrams;
	private Map<PlcAddress, String> addressesToResourceId;

	public DefaultPlcHandler(ComponentContainer container, String componentName) {
		super(container, componentName);
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
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("PlcAddress for " + resource + "-" + action + " does not exist!");
		return plcAddress;
	}

	@Override
	public String getPlcAddressId(String resource, String action) {
		PlcAddress plcAddress = getPlcAddress(resource, action);
		String addressId = this.addressesToResourceId.get(plcAddress);
		if (addressId == null)
			throw new IllegalStateException(
					"PlcAddress mapping ID for " + resource + "-" + action + " does not exist!");
		return addressId;
	}

	@Override
	public void initialize(ComponentConfiguration configuration) throws Exception {

		// validate Plc class name
		String plcClassName = configuration.getString("plcClass", DefaultPlc.class.getName());
		Class.forName(plcClassName);

		this.plcState = PlcState.Initial;
		this.plcStateMsg = PlcState.Initial.name();
		this.virtualListeners = new HashMap<>();
		this.plcAddresses = new MapOfMaps<>();
		this.plcTelegrams = new MapOfMaps<>();
		this.addressesToResourceId = new HashMap<>();

		super.initialize(configuration);
	}

	@Override
	public void start() throws Exception {
		this.ctx = getContainer().getPrivilegeHandler().openAgentSystemUserContext();
		if (reconfigurePlc())
			startPlc();
		super.start();
	}

	@Override
	public void stop() throws Exception {
		stopPlc();
		if (this.ctx != null)
			getContainer().getPrivilegeHandler().invalidate(this.ctx.getCertificate());
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
			this.plcAddresses = plcAddresses;
			this.plcTelegrams = plcTelegrams;
			this.addressesToResourceId = addressesToResourceId;

			this.plcState = PlcState.Configured;
			this.plcStateMsg = PlcState.Configured.name();

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
			MapOfMaps<String, String, PlcAddress> plcTelegrams, Map<PlcAddress, String> addressesToResourceId) {

		Plc plc;
		try (StrolchTransaction tx = openTx(ctx.getCertificate(), true)) {

			String plcClassName = getConfiguration().getString("plcClass", DefaultPlc.class.getName());

			plc = PlcConfigurator.configurePlc(tx, plcClassName, plcAddresses, plcTelegrams, addressesToResourceId);
			plc.setConnectionStateChangeListener(this);
			plcAddresses.values().stream().filter(a -> a.type == PlcAddressType.Notification)
					.forEach(plcAddress -> plc.registerListener(plcAddress, this::asyncStateUpdate));

			if (tx.needsCommit())
				tx.commitOnClose();
		}

		return plc;
	}

	private void asyncStateUpdate(PlcAddress address, Object value) {
		getExecutorService("PlcAddressUpdater").submit(() -> updatePlcAddress(address, value));
	}

	private void asyncStateUpdate(PlcConnection connection) {
		getExecutorService("PlcConnectionUpdater").submit(() -> updateConnectionState(connection));
	}

	private void updatePlcAddress(PlcAddress address, Object value) {
		long s = nanoTime();

		String addressId = this.addressesToResourceId.get(address);
		if (addressId == null) {
			logger.error("No PlcAddress mapping for " + address);
			return;
		}

		try {
			try (StrolchTransaction tx = openTx(validateCtx().getCertificate(), "updatePlcAddress", false)) {
				tx.lock(Resource.locatorFor(TYPE_PLC_ADDRESS, addressId));
				Resource addressRes = tx.getResourceBy(TYPE_PLC_ADDRESS, addressId, true);

				// see if we need to invert a boolean flag
				if (address.valueType == PlcValueType.Boolean && address.inverted) {
					value = !((boolean) value);
				}

				Parameter<?> valueP = addressRes.getParameter(PARAM_VALUE, true);
				logger.info("PlcAddress {}-{} has changed from {} to {}", address.resource, address.action,
						valueP.getValue(), value);

				valueP.accept(new SetParameterValueVisitor(value));
				tx.update(addressRes);
				tx.commitOnClose();
			}
		} catch (Exception e) {
			logger.error("Failed to update PlcAddress " + addressId + " with new value " + value, e);
		}

		logger.info("async update " + address.address + " took " + (formatNanoDuration(nanoTime() - s)));
	}

	private void updateConnectionState(PlcConnection plcConnection) {
		long s = nanoTime();

		try {
			try (StrolchTransaction tx = openTx(validateCtx().getCertificate(), "updateConnectionState", false)) {
				tx.lock(Resource.locatorFor(TYPE_PLC_CONNECTION, plcConnection.getId()));
				Resource connection = tx.getResourceBy(TYPE_PLC_CONNECTION, plcConnection.getId());
				updateConnectionState(tx, connection, plcConnection);
				tx.update(connection);
				tx.commitOnClose();
			}
		} catch (Exception e) {
			logger.error("Failed to update state for connection " + plcConnection.getId(), e);
		}

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
	public Collection<PlcAddress> getVirtualAddresses() {
		return this.virtualListeners.keySet();
	}

	@Override
	public void registerVirtualListener(String resource, String action, PlcListener listener, PlcValueType valueType,
			Object defaultValue) {
		if (this.plcAddresses.containsElement(resource, action))
			throw new IllegalStateException(
					"There already is a virtual listener registered for key " + resource + "-" + action);
		PlcAddress plcAddress = new PlcAddress(PlcAddressType.Notification, true, resource, action,
				listener.getClass().getSimpleName(), valueType, defaultValue, false);
		this.plcAddresses.addElement(resource, action, plcAddress);
		this.virtualListeners.put(plcAddress, listener);
		logger.info("Registered virtual listener for " + resource + "-" + action);
	}

	@Override
	public void unregisterVirtualListener(String resource, String action) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null) {
			logger.error("No PlcListener registered for " + resource + " " + action);
			return;
		}

		PlcListener listener = this.virtualListeners.remove(plcAddress);
		if (listener == null) {
			logger.error("No PlcListener registered for " + resource + " " + action);
			return;
		}

		this.plcAddresses.removeElement(resource, action);
		logger.info("Unregistered listener " + resource + " " + action);
	}

	@Override
	public void registerListener(String resource, String action, PlcListener listener) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcAddress exists for " + resource + "-" + action);
		this.plc.registerListener(plcAddress, listener);
	}

	@Override
	public void unregisterListener(String resource, String action, PlcListener listener) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null) {
			logger.warn("No PlcAddress exists for " + resource + "-" + action);
		} else {
			this.plc.unregisterListener(plcAddress, listener);
		}
	}

	@Override
	public void send(String resource, String action) {
		PlcAddress plcAddress = this.plcTelegrams.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcTelegram exists for " + resource + "-" + action);

		if (plcAddress.virtual) {
			this.virtualListeners.get(plcAddress).handleNotification(plcAddress, plcAddress.defaultValue);
			return;
		}

		if (plcAddress.defaultValue == null)
			throw new IllegalStateException("Can not send PlcAddress as no default value set for " + plcAddress);

		logger.info("Sending " + resource + "-" + action + ": " + plcAddress.defaultValue + " (default)");
		PlcConnection connection = validateConnection(plcAddress);
		connection.send(plcAddress.address, plcAddress.defaultValue);
		asyncStateUpdate(plcAddress, plcAddress.defaultValue);
	}

	@Override
	public void send(String resource, String action, Object value) {
		PlcAddress plcAddress = this.plcTelegrams.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcTelegram exists for " + resource + "-" + action);

		if (plcAddress.virtual) {
			this.virtualListeners.get(plcAddress).handleNotification(plcAddress, value);
			return;
		}

		logger.info("Sending " + resource + "-" + action + ": " + value);
		PlcConnection connection = validateConnection(plcAddress);
		connection.send(plcAddress.address, value);
		asyncStateUpdate(plcAddress, value);
	}

	@Override
	public void notify(String resource, String action, Object value) {
		PlcAddress plcAddress = this.plcAddresses.getElement(resource, action);
		if (plcAddress == null)
			throw new IllegalStateException("No PlcAddress exists for " + resource + "-" + action);

		if (plcAddress.type != PlcAddressType.Notification)
			throw new IllegalStateException(
					"Can not notify PlcAddress " + plcAddress + " as it is not a notification!");

		if (plcAddress.virtual) {
			this.virtualListeners.get(plcAddress).handleNotification(plcAddress, value);
			return;
		}

		this.plc.notify(plcAddress.address, value);
	}

	private PlcConnection validateConnection(PlcAddress plcAddress) {
		PlcConnection connection = this.plc.getConnection(plcAddress);
		if (connection.getState() == ConnectionState.Connected)
			return connection;

		connection.connect();

		if (connection.getState() == ConnectionState.Connected)
			return connection;

		throw new IllegalStateException("PlcConnection " + connection.getId() + " is disconnected for " + plcAddress);
	}

	@Override
	public StrolchTransaction openTx(Certificate cert, boolean readOnly) {
		return super.openTx(cert, readOnly);
	}

	@Override
	public void notifyStateChange(PlcConnection connection) {
		asyncStateUpdate(connection);
	}
}
