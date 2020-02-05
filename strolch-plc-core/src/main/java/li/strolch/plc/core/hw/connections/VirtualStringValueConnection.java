package li.strolch.plc.core.hw.connections;

import li.strolch.plc.core.hw.Plc;

public class VirtualStringValueConnection extends SimplePlcConnection {

	public VirtualStringValueConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void send(String address, Object value) {
		String string = (String) value;
		logger.info("Setting address " + this.id + " to " + string);
		// this.plc.notify(this.id, string);
	}
}
