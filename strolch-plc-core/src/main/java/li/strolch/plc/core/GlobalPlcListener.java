package li.strolch.plc.core;

import li.strolch.plc.core.hw.PlcListener;
import li.strolch.plc.model.MessageState;
import li.strolch.utils.I18nMessage;

public interface GlobalPlcListener extends PlcListener {

	void sendMsg(I18nMessage msg, MessageState state);
}
