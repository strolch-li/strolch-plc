package li.strolch.plc.model;

public class PlcResponse {

	private static long lastSequenceId = System.currentTimeMillis();

	protected final String plcId;
	protected final long sequenceId;
	protected PlcResponseState state;
	protected String stateMsg;

	private Runnable listener;

	public PlcResponse(String plcId) {
		this.plcId = plcId;
		this.sequenceId = lastSequenceId++;
		this.state = PlcResponseState.Pending;
		this.stateMsg = "";
	}

	public String getPlcId() {
		return this.plcId;
	}

	public long getSequenceId() {
		return this.sequenceId;
	}

	public PlcResponseState getState() {
		return this.state;
	}

	public void setState(PlcResponseState state) {
		this.state = state;
	}

	public String getStateMsg() {
		return this.stateMsg;
	}

	public void setStateMsg(String stateMsg) {
		this.stateMsg = stateMsg;
	}

	public Runnable getListener() {
		return this.listener;
	}

	public void setListener(Runnable listener) {
		this.listener = listener;
	}
}
