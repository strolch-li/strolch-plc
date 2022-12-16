package li.strolch.plc.gw.server;

import li.strolch.plc.model.PlcAddressKey;

public interface PlcNotificationListener {

	void handleNotification(PlcAddressKey addressKey, Object value) throws Exception;

	default void handleConnectionLost() {
		// no-op
	}
}
