package li.strolch.plc.gw.server;

import li.strolch.plc.model.ConnectionState;

public interface PlcConnectionStateListener {

	void handleConnectionState(String plcId, ConnectionState connectionState);
}
