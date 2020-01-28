package li.strolch.plc.core.hw;

import java.util.Map;
import java.util.Set;

import li.strolch.plc.model.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PlcConnection {

	protected static final Logger logger = LoggerFactory.getLogger(PlcConnection.class);

	protected final Plc plc;
	protected final String id;
	protected ConnectionState connectionState;
	protected String connectionStateMsg;

	public PlcConnection(Plc plc, String id) {
		this.plc = plc;
		this.id = id;
		this.connectionState = ConnectionState.Disconnected;
	}

	public String getId() {
		return this.id;
	}

	public ConnectionState getState() {
		return this.connectionState;
	}

	public String getStateMsg() {
		return this.connectionStateMsg;
	}

	public abstract void initialize(Map<String, Object> parameters);

	public abstract void connect();

	public abstract void disconnect();

	public abstract void send(String address, Object value);

	public abstract Set<String> getAddresses();

	protected void assertConnected() {
		if (this.connectionState != ConnectionState.Connected)
			throw new IllegalStateException("PlcConnection " + this.id + " is not yet connected!");
	}
}
