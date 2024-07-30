package li.strolch.plc.model;

import static java.text.MessageFormat.format;

public class PlcAddressResponse extends PlcResponse {

	private final PlcAddressKey plcAddressKey;

	public PlcAddressResponse(String plcId, PlcAddressKey plcAddressKey) {
		super(plcId);
		this.plcAddressKey = plcAddressKey;
	}

	@Override
	public PlcAddressResponse state(PlcResponseState state, String stateMsg) {
		super.state(state, stateMsg);
		return this;
	}

	public PlcAddressKey getPlcAddressKey() {
		return this.plcAddressKey;
	}

	@Override
	public String toString() {
		return format("PlcAddressResponse'{'plcId=''{0}'', plcAddressKey={1}, sequenceId={2}, state={3}'}'", plcId,
				plcAddressKey, sequenceId, state);
	}
}
