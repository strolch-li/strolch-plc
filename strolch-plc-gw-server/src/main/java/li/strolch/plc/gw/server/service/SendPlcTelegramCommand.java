package li.strolch.plc.gw.server.service;

import li.strolch.model.StrolchValueType;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.gw.server.PlcGwServerHandler;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressResponse;
import li.strolch.service.api.Command;
import li.strolch.utils.dbc.DBC;

import static li.strolch.plc.model.PlcConstants.*;

public class SendPlcTelegramCommand extends Command {

	private String plcId;
	private String resource;
	private String action;
	private String valueTypeS;
	private String valueS;

	private PlcAddressResponse response;

	public SendPlcTelegramCommand(StrolchTransaction tx) {
		super(tx);
	}

	public void setPlcId(String plcId) {
		this.plcId = plcId;
	}

	public void setResource(String resource) {
		this.resource = resource;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public void setValueType(String valueType) {
		this.valueTypeS = valueType;
	}

	public void setValue(String value) {
		this.valueS = value;
	}

	public PlcAddressResponse getResponse() {
		return this.response;
	}

	@Override
	public void validate() {
		DBC.PRE.assertNotEmpty(PARAM_PLC_ID + " must be set!", this.plcId);
		DBC.PRE.assertNotEmpty(PARAM_RESOURCE + " must be set!", this.resource);
		DBC.PRE.assertNotEmpty(PARAM_ACTION + " must be set!", this.action);
		if (this.valueS != null) {
			DBC.PRE.assertNotEmpty(PARAM_VALUE + " must either not be set, or a non-empty value", valueS);
			DBC.PRE.assertNotEmpty(PARAM_VALUE_TYPE + " must be set when a value is given!", valueTypeS);
		}
	}

	@Override
	public void doCommand() {

		PlcGwServerHandler plcHandler = getComponent(PlcGwServerHandler.class);
		if (!plcHandler.isPlcConnected(plcId))
			throw new IllegalStateException("PLC " + this.plcId + " is not connected!");

		PlcAddressKey addressKey = PlcAddressKey.keyFor(this.resource, this.action);

		// sending without a value
		if (this.valueS == null) {
			this.response = plcHandler.sendMessageSync(addressKey, this.plcId);
			return;
		}

		// sending with a value
		StrolchValueType valueType = StrolchValueType.parse(this.valueTypeS);
		this.response = plcHandler.sendMessageSync(addressKey, this.plcId, valueType.parseValue(valueS));
	}
}
