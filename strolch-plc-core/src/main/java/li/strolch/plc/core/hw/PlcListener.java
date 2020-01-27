package li.strolch.plc.core.hw;

public interface PlcListener {

	void handleNotification(PlcAddress address, Object value);
}
