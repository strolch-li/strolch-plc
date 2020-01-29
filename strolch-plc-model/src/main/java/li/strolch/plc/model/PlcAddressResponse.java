package li.strolch.plc.model;

public class PlcAddressResponse extends PlcResponse {

	private PlcAddressKey plcAddressKey;

	public PlcAddressResponse(String plcId, PlcAddressKey plcAddressKey) {
		super(plcId);
		this.plcAddressKey = plcAddressKey;
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
