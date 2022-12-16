package li.strolch.plc.model;

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
		return "PlcAddressResponse{" + "plcId='" + plcId + '\'' + ", plcAddressKey=" + plcAddressKey + ", sequenceId="
				+ sequenceId + ", state=" + state + '}';
	}
}
