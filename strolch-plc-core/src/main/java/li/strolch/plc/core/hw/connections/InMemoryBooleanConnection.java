package li.strolch.plc.core.hw.connections;

import li.strolch.plc.core.hw.Plc;

public class InMemoryBooleanConnection extends SimplePlcConnection {

	public InMemoryBooleanConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void send(String address, Object value) {
		boolean bool = (boolean) value;
		logger.info("Setting address " + this.id + " to " + bool);
		this.plc.notify(this.id, bool);
	}
}
