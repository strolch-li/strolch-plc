package li.strolch.plc.core.hw.connections;

import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.model.ConnectionState;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public abstract class SimplePlcConnection extends PlcConnection {

	protected boolean simulated;

	public SimplePlcConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) throws Exception {
		logger.info("Configured {} {}", getClass().getSimpleName(), this.id);
	}

	@Override
	public boolean connect() {
		logger.info("{}: Is now connected.", this.id);
		if (this.simulated)
			logger.info("Running SIMULATED");
		this.connectionState = ConnectionState.Connected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
		return true;
	}

	@Override
	public void disconnect() {
		logger.info("{}: Is now disconnected.", this.id);
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
