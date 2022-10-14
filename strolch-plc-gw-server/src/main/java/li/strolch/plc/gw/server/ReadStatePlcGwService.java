package li.strolch.plc.gw.server;

import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressValueResponse;

public abstract class ReadStatePlcGwService extends PlcGwService implements PlcConnectionStateListener {

	public ReadStatePlcGwService(String plcId, PlcGwServerHandler plcHandler) {
		super(plcId, plcHandler);
	}

	protected void handleGetState(PlcAddressValueResponse response) {
		PlcAddressKey addressKey = response.getPlcAddressKey();
		if (response.isFailed()) {
			logger.error("Failed to read value for address " + addressKey + ": " + response.getStateMsg());
		} else {
			storeAddressState(addressKey, response.getValue());
		}
	}

	protected void readState(String resource, String action) {
		this.plcHandler.asyncGetAddressState(keyFor(resource, action), this.plcId, this::handleGetState);
	}

	protected abstract void storeAddressState(PlcAddressKey addressKey, Object value);
}
