package li.strolch.plc.model;

public interface PlcNotificationListener {

	void handleNotification(PlcAddressKey addressKey, Object value) throws Exception;

	default void handleConnectionLost() {
		// no-op
	}
}
