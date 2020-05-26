package li.strolch.plc.core.hw.connections;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.model.ConnectionState;

public abstract class SimplePlcConnection extends PlcConnection {

	protected boolean simulated;

	public SimplePlcConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) throws Exception {
		logger.info("Configured " + getClass().getSimpleName() + " " + this.id);
	}

	@Override
	public boolean connect() {
		logger.info(this.id + ": Is now connected.");
		if (this.simulated)
			logger.info("Running SIMULATED");
		this.connectionState = ConnectionState.Connected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
		return true;
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
