package li.strolch.plc.gw.server.policy.execution;

import static li.strolch.plc.gw.server.PlcServerContants.BUNDLE_STROLCH_PLC_GW_SERVER;
import static li.strolch.runtime.StrolchConstants.SYSTEM_USER_AGENT;

import li.strolch.execution.policy.SimpleExecution;
import li.strolch.handler.operationslog.LogMessage;
import li.strolch.handler.operationslog.LogSeverity;
import li.strolch.model.activity.Action;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.gw.server.PlcGwServerHandler;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcNotificationListener;

public abstract class PlcExecutionPolicy extends SimpleExecution implements PlcNotificationListener {

	protected String realm;

	protected PlcGwServerHandler plcHandler;

	public PlcExecutionPolicy(StrolchTransaction tx) {
		super(tx);
		this.realm = tx.getRealmName();
	}

	protected abstract String getPlcId();

	@Override
	public void initialize(Action action) {
		super.initialize(action);
		this.plcHandler = getComponent(PlcGwServerHandler.class);
	}

	public String getActionType() {
		return this.actionType;
	}

	protected void register() {
		// do nothing
	}

	protected void unregister() {
		// do nothing
	}

	@Override
	protected void handleStopped() {
		unregister();
		super.handleStopped();
	}

	protected void toExecuted() throws Exception {
		stop();
		getController().toExecuted(this.actionLoc);
	}

	protected boolean assertPlcConnected() {
		if (this.plcHandler.isPlcConnected(getPlcId()))
			return true;

		toError(msgPlcNotConnected(this.realm));
		return false;
	}

	@Override
	public abstract void handleNotification(PlcAddressKey addressKey, Object value);

	@Override
	public void handleConnectionLost() {
		toError(msgConnectionLostToPlc(this.realm));
	}

	protected LogMessage msgPlcNotConnected(String realm) {
		return new LogMessage(realm, SYSTEM_USER_AGENT, this.actionLoc, LogSeverity.Warning,
				BUNDLE_STROLCH_PLC_GW_SERVER, "execution.plc.notConnected").value("plc", getPlcId());
	}

	protected LogMessage msgConnectionLostToPlc(String realm) {
		return new LogMessage(realm, SYSTEM_USER_AGENT, this.actionLoc, LogSeverity.Error, BUNDLE_STROLCH_PLC_GW_SERVER,
				"execution.plc.connectionLost").value("plc", getPlcId());
	}
}
