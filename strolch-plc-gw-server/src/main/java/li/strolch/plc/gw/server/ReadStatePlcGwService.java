package li.strolch.plc.gw.server;

import li.strolch.model.Resource;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressValueResponse;

import static li.strolch.plc.model.PlcConstants.PARAM_PLC_SUPPORTS_READ_STATE;

public abstract class ReadStatePlcGwService extends PlcGwService implements PlcConnectionStateListener {

	protected boolean plcSupportsReadState;

	public ReadStatePlcGwService(String plcId, PlcGwServerHandler plcHandler) {
		super(plcId, plcHandler);
	}

	protected void handleGetState(PlcAddressValueResponse response) {
		PlcAddressKey addressKey = response.getPlcAddressKey();
		if (response.isFailed()) {
			logger.error("Failed to read value for address {}: {}", addressKey, response.getStateMsg());
		} else {
			storeAddressState(addressKey, response.getValue());
		}
	}

	@Override
	public void register() {
		this.plcSupportsReadState = runReadOnlyTx((ctx, tx) -> {
			Resource configuration = tx.getConfiguration();
			return !configuration.hasParameter(PARAM_PLC_SUPPORTS_READ_STATE) || configuration.getBoolean(
					PARAM_PLC_SUPPORTS_READ_STATE);
		});
		super.register();
	}

	protected void readState(String resource, String action) {
		if (!this.plcSupportsReadState)
			logger.warn("Not reading state for resource {}: {} as PLC does not support this feature!", resource,
					action);
		else
			this.plcHandler.asyncGetAddressState(keyFor(resource, action), this.plcId, this::handleGetState);
	}

	protected abstract void storeAddressState(PlcAddressKey addressKey, Object value);
}
