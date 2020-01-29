package li.strolch.plc.model;

public interface PlcNotificationListener {

	void handleNotification(PlcAddressKey addressKey, Object value);

	void handleConnectionLost();
}
