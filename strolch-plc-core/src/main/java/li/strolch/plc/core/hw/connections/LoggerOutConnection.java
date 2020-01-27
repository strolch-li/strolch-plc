package li.strolch.plc.core.hw.connections;

import li.strolch.plc.core.hw.Plc;

public class LoggerOutConnection extends SimplePlcConnection {

	public LoggerOutConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void send(String address, Object value) {
		assertConnected();
		logger.info(address + " -> " + value);
	}
}
