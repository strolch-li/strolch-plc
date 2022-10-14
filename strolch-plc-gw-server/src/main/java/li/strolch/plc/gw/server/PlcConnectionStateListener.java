package li.strolch.plc.gw.server;

import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.model.PlcAddressKey;

public interface PlcConnectionStateListener {

	void handleConnectionState(String plcId, ConnectionState connectionState);
}
