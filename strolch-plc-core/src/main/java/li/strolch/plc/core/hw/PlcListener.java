package li.strolch.plc.core.hw;

import li.strolch.plc.model.PlcAddress;

public interface PlcListener {

	void handleNotification(PlcAddress address, Object value);
}
