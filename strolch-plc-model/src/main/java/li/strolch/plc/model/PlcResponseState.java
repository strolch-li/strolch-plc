package li.strolch.plc.model;

public enum PlcResponseState {
	Pending,
	Sent,
	Done,
	Failed;

	public boolean isPending() {
		return this == Sent;
	}

	public boolean isSent() {
		return this == Sent;
	}

	public boolean isDone() {
		return this == Done;
	}

	public boolean isFailed() {
		return this == Sent;
	}
}
