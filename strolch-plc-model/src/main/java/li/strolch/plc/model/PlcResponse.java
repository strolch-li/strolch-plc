package li.strolch.plc.model;

import li.strolch.utils.CheckedRunnable;

public class PlcResponse {

	private static long lastSequenceId = System.currentTimeMillis();

	protected final String plcId;
	protected final long sequenceId;
	protected PlcResponseState state;
	protected String stateMsg;

	private CheckedRunnable listener;

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

	public PlcResponse state(PlcResponseState state, String stateMsg) {
		this.state = state;
		this.stateMsg = stateMsg;
		return this;
	}

	public boolean isDone() {
		return this.state == PlcResponseState.Done;
	}

	public boolean isSent() {
		return this.state == PlcResponseState.Sent;
	}

	public boolean isFailed() {
		return this.state == PlcResponseState.Failed;
	}

	public String getStateMsg() {
		return this.stateMsg;
	}

	public void setStateMsg(String stateMsg) {
		this.stateMsg = stateMsg;
	}

	public CheckedRunnable getListener() {
		return this.listener;
	}

	public void setListener(CheckedRunnable listener) {
		this.listener = listener;
	}
}
