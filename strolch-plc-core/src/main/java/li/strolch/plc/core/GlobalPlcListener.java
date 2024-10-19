package li.strolch.plc.core;

import li.strolch.model.Locator;
import li.strolch.model.log.LogMessage;
import li.strolch.plc.core.hw.PlcListener;

public interface GlobalPlcListener extends PlcListener {

	void sendMsg(LogMessage message);

	void disableMsg(Locator locator);
}
