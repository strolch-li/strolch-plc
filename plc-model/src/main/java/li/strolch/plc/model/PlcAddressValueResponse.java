package li.strolch.plc.model;

public class PlcAddressValueResponse extends PlcAddressResponse {

	private Object value;

	public PlcAddressValueResponse(String plcId, PlcAddressKey plcAddressKey) {
		super(plcId, plcAddressKey);
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public Object getValue() {
		return this.value;
	}

	public boolean getValueAsBoolean() {
		return (Boolean) this.value;
	}

	public int getValueAsInt() {
		return ((Number) this.value).intValue();
	}

	public double getValueAsDouble() {
		return ((Number) this.value).doubleValue();
	}

	public String getValueAsString() {
		return ((String) this.value);
	}

	@Override
	public PlcAddressValueResponse state(PlcResponseState state, String stateMsg) {
		super.state(state, stateMsg);
		return this;
	}

	@Override
	public String toString() {
		return "PlcAddressValueResponse{" + "plcId='" + plcId + '\'' + ", sequenceId=" + sequenceId + ", state=" + state
				+ ", stateMsg='" + stateMsg + '\'' + ", value=" + value + '}';
	}
}
