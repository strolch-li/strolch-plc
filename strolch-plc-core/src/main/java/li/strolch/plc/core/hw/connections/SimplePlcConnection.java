package li.strolch.plc.core.hw.connections;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;

public abstract class SimplePlcConnection extends PlcConnection {
	public SimplePlcConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		logger.info("Configured " + getClass().getSimpleName() + this.id);
	}

	@Override
	public void connect() {
		logger.info(this.id + ": Is now connected.");
		this.connectionState = ConnectionState.Connected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	@Override
	public void disconnect() {
		logger.info(this.id + ": Is now disconnected.");
		this.connectionState = ConnectionState.Disconnected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	@Override
	public Set<String> getAddresses() {
		TreeSet<String> addresses = new TreeSet<>();
		addresses.add(this.id);
		return Collections.unmodifiableSet(addresses);
	}
}
