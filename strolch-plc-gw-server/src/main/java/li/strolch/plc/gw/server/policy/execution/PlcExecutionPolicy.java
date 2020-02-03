package li.strolch.plc.gw.server.policy.execution;

import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;
import static li.strolch.plc.gw.server.PlcServerContants.BUNDLE_STROLCH_PLC_GW_SERVER;
import static li.strolch.plc.model.PlcConstants.PARAM_PLC_ID;
import static li.strolch.runtime.StrolchConstants.SYSTEM_USER_AGENT;

import li.strolch.execution.policy.SimpleExecution;
import li.strolch.handler.operationslog.LogMessage;
import li.strolch.handler.operationslog.LogSeverity;
import li.strolch.model.Locator;
import li.strolch.model.activity.Action;
import li.strolch.model.parameter.StringParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.gw.server.PlcGwServerHandler;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcNotificationListener;

public abstract class PlcExecutionPolicy extends SimpleExecution implements PlcNotificationListener {

	private String realm;

	private Locator actionLoc;
	private String plcId;
	private PlcGwServerHandler plcHandler;
	private PlcAddressKey addressKey;

	public PlcExecutionPolicy(StrolchTransaction tx) {
		super(tx);
		this.realm = tx.getRealmName();
	}

	protected void initialize(Action action) {
		this.actionLoc = action.getLocator();

		// set all fields
		getPlcId(action);
		getPlcHandler();
		getAddressKey(action);
	}

	public Locator getActionLoc() {
		return this.actionLoc;
	}

	protected String getPlcId(Action action) {
		if (this.plcId == null) {
			StringParameter plcIdP = action.findParameter(BAG_PARAMETERS, PARAM_PLC_ID, true);
			this.plcId = plcIdP.getValue();
		}

		return this.plcId;
	}

	protected PlcAddressKey getAddressKey(Action action) {
		if (this.addressKey == null)
			this.addressKey = PlcAddressKey.valueOf(action.getResourceId(), action.getType());
		return this.addressKey;
	}

	protected PlcGwServerHandler getPlcHandler() {
		if (this.plcHandler == null)
			this.plcHandler = getComponent(PlcGwServerHandler.class);
		return this.plcHandler;
	}

	protected void register(Action action) {
		getPlcHandler().register(getAddressKey(action), getPlcId(action), this);
	}

	protected void unregister(Action action) {
		getPlcHandler().unregister(getAddressKey(action), getPlcId(action), this);
	}

	protected boolean assertPlcConnected(Action action) {
		if (getPlcHandler().isPlcConnected(getPlcId(action)))
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
				BUNDLE_STROLCH_PLC_GW_SERVER, "execution.plc.notConnected").value("plc", this.plcId);
	}

	protected LogMessage msgConnectionLostToPlc(String realm) {
		return new LogMessage(realm, SYSTEM_USER_AGENT, getActionLoc(), LogSeverity.Error, BUNDLE_STROLCH_PLC_GW_SERVER,
				"execution.plc.connectionLost").value("plc", this.plcId);
	}
}
