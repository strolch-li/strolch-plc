package li.strolch.plc.core;

import li.strolch.handler.operationslog.LogMessage;
import li.strolch.model.Locator;
import li.strolch.plc.core.hw.PlcListener;

public interface GlobalPlcListener extends PlcListener {

	void sendMsg(LogMessage message);

	void disableMsg(Locator locator);
}
