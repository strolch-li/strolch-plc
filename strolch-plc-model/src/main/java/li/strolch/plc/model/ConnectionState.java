package li.strolch.plc.model;

public enum ConnectionState {
	Connected,
	Disconnected,
	Failed;

	public boolean isConnected() {
		return this == Connected;
	}

	public boolean isDisconnected() {
		return this == Disconnected;
	}

	public boolean isFailed() {
		return this == Failed;
	}
}
