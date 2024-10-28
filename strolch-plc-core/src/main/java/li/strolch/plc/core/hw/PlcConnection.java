package li.strolch.plc.core.hw;

import li.strolch.plc.model.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

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

	public abstract void initialize(Map<String, Object> parameters) throws Exception;

	public abstract boolean connect();

	public abstract void disconnect();

	public abstract void send(String address, Object value);

	public abstract Set<String> getAddresses();

	protected void assertConnected() {
		if (this.connectionState != ConnectionState.Connected)
			throw new IllegalStateException("PlcConnection " + this.id + " is not yet connected!");
	}

	public boolean isAutoConnect() {
		return true;
	}

	protected boolean isConnected() {
		return this.connectionState == ConnectionState.Connected;
	}

	protected void handleBrokenConnection(String errorMsg, Throwable e) {
		if (e == null)
			logger.error(errorMsg);
		else
			logger.error(errorMsg, e);
		this.connectionState = ConnectionState.Failed;
		this.connectionStateMsg = errorMsg;
		this.plc.notifyConnectionStateChanged(this);
	}

	protected void notify(String address, Object value) {
		this.plc.syncNotify(address, value);
	}

	@Override
	public String toString() {
		return this.id;
	}
}
