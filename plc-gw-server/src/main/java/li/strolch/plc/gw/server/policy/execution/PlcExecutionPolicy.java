package li.strolch.plc.gw.server.policy.execution;

import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;
import static li.strolch.model.log.LogMessageState.*;
import static li.strolch.plc.gw.server.PlcGwSrvI18n.*;
import static li.strolch.plc.model.PlcConstants.PARAM_PLC_ID;
import static li.strolch.runtime.StrolchConstants.SYSTEM_USER_AGENT;

import java.util.HashSet;
import java.util.Set;

import li.strolch.execution.ExecutionHandler;
import li.strolch.execution.policy.SimpleExecution;
import li.strolch.model.activity.Action;
import li.strolch.model.log.LogMessage;
import li.strolch.model.log.LogMessageState;
import li.strolch.model.log.LogSeverity;
import li.strolch.model.parameter.StringParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.gw.server.PlcAddressResponseListener;
import li.strolch.plc.gw.server.PlcGwServerHandler;
import li.strolch.plc.gw.server.PlcGwSrvI18n;
import li.strolch.plc.gw.server.PlcNotificationListener;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressResponse;

public abstract class PlcExecutionPolicy extends SimpleExecution
		implements PlcNotificationListener, PlcAddressResponseListener {

	protected PlcGwServerHandler plcHandler;
	protected Set<PlcAddressKey> registeredKeys;
	private String plcId;

	public PlcExecutionPolicy(StrolchTransaction tx) {
		super(tx);
		this.registeredKeys = new HashSet<>();
	}

	protected String getPlcId() {
		return this.plcId;
	}

	@Override
	public void initialize(Action action) {
		super.initialize(action);
		StringParameter plcIdP = action.findParameter(BAG_PARAMETERS, PARAM_PLC_ID, true);
		this.plcId = plcIdP.getValue();
		this.plcHandler = getComponent(PlcGwServerHandler.class);
	}

	public String getActionType() {
		return this.actionType;
	}

	protected void register(PlcAddressKey key) {
		this.plcHandler.register(getPlcId(), key, this);
		this.registeredKeys.add(key);
	}

	protected void unregister(PlcAddressKey key) {
		this.plcHandler.unregister(getPlcId(), key, this);
		this.registeredKeys.remove(key);
	}

	protected void unregisterAll() {
		this.registeredKeys.forEach(k -> this.plcHandler.unregister(getPlcId(), k, this));
	}

	@Override
	protected void handleStopped() {
		unregisterAll();
		super.handleStopped();
	}

	protected boolean isPlcConnected() {
		return this.plcHandler.isPlcConnected(getPlcId());
	}

	protected boolean assertPlcConnected() {
		if (this.plcHandler.isPlcConnected(getPlcId()))
			return true;

		toError(msgPlcNotConnected());
		return false;
	}

	protected boolean assertResponse(PlcAddressResponse response) {
		if (response.getState().isFailed()) {
			toError(msgFailedToSendMessage(response));
			return false;
		}

		return true;
	}

	protected void send(PlcAddressKey key, boolean value) {
		this.plcHandler.sendMessage(key, getPlcId(), value, this);
	}

	protected void send(PlcAddressKey key, int value) {
		this.plcHandler.sendMessage(key, getPlcId(), value, this);
	}

	protected void send(PlcAddressKey key, double value) {
		this.plcHandler.sendMessage(key, getPlcId(), value, this);
	}

	protected void send(PlcAddressKey key, String value) {
		this.plcHandler.sendMessage(key, getPlcId(), value, this);
	}

	protected void send(PlcAddressKey key) {
		this.plcHandler.sendMessage(key, getPlcId(), this);
	}

	@Override
	public void handleResponse(PlcAddressResponse response) throws Exception {
		assertResponse(response);
	}

	@Override
	public abstract void handleNotification(PlcAddressKey key, Object value) throws Exception;

	@Override
	public void handleConnectionLost() {
		toError(msgConnectionLostToPlc());
	}

	protected LogMessage msgPlcNotConnected() {
		return new LogMessage(this.realm, getLogMessageUsername(), this.actionLoc, LogSeverity.Error, Information,
				bundle, "execution.plc.notConnected").value("plc", getPlcId());
	}

	protected String getLogMessageUsername() {
		return SYSTEM_USER_AGENT;
	}

	protected LogMessage msgFailedToSendMessage(PlcAddressResponse response) {
		PlcAddressKey key = response.getPlcAddressKey();
		return new LogMessage(this.realm, getLogMessageUsername(), this.actionLoc, LogSeverity.Error, Information,
				bundle, "execution.plc.sendMessage.failed") //
				.value("plc", getPlcId()) //
				.value("key", key) //
				.value("msg", response.getStateMsg());
	}

	protected LogMessage msgConnectionLostToPlc() {
		return new LogMessage(this.realm, getLogMessageUsername(), this.actionLoc, LogSeverity.Error, Information,
				bundle, "execution.plc.connectionLost").value("plc", getPlcId());
	}
}
