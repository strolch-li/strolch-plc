package li.strolch.plc.gw.server.policy.execution;

import static li.strolch.plc.gw.server.PlcServerContants.BUNDLE_STROLCH_PLC_GW_SERVER;
import static li.strolch.runtime.StrolchConstants.SYSTEM_USER_AGENT;

import li.strolch.execution.policy.SimpleExecution;
import li.strolch.handler.operationslog.LogMessage;
import li.strolch.handler.operationslog.LogSeverity;
import li.strolch.model.Locator;
import li.strolch.model.activity.Action;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.gw.server.PlcGwServerHandler;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcNotificationListener;
import li.strolch.utils.helper.StringHelper;

public abstract class PlcExecutionPolicy extends SimpleExecution implements PlcNotificationListener {

	protected String realm;

	protected String actionType;
	protected Locator actionLoc;

	protected PlcGwServerHandler plcHandler;

	public PlcExecutionPolicy(StrolchTransaction tx) {
		super(tx);
		this.realm = tx.getRealmName();
	}

	protected abstract String getPlcId();

	protected void initialize(Action action) {
		this.actionType = action.getType();
		this.actionLoc = action.getLocator();
		this.plcHandler = getComponent(PlcGwServerHandler.class);
	}

	protected void toExecuted() {

		unregister();

		long delay = 5L;
		logger.info(
				"Delaying toExecuted of " + getActionLoc() + " by " + StringHelper.formatMillisecondsDuration(delay));
		getDelayedExecutionTimer().execute(this.realm, getContainer(), getActionLoc(), delay);
	}

	public String getActionType() {
		return this.actionType;
	}

	public Locator getActionLoc() {
		return this.actionLoc;
	}

	protected void register() {
		// do nothing
	}

	protected void unregister() {
		// do nothing
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
		return new LogMessage(realm, SYSTEM_USER_AGENT, getActionLoc(), LogSeverity.Warning,
				BUNDLE_STROLCH_PLC_GW_SERVER, "execution.plc.notConnected").value("plc", getPlcId());
	}

	protected LogMessage msgConnectionLostToPlc(String realm) {
		return new LogMessage(realm, SYSTEM_USER_AGENT, getActionLoc(), LogSeverity.Error, BUNDLE_STROLCH_PLC_GW_SERVER,
				"execution.plc.connectionLost").value("plc", getPlcId());
	}
}
