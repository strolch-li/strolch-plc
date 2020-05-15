package li.strolch.plc.core;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcListener;
import li.strolch.plc.model.MessageState;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;
import li.strolch.utils.I18nMessage;

public interface PlcHandler {

	ComponentContainer getContainer();

	PlcState getPlcState();

	String getPlcStateMsg();

	boolean reconfigurePlc();

	void startPlc();

	void stopPlc();

	Plc getPlc();

	PlcAddress getPlcAddress(String resource, String action);

	String getPlcAddressId(String resource, String action);

	void setGlobalListener(GlobalPlcListener listener);

	void register(String resource, String action, PlcListener listener);

	void unregister(String resource, String action, PlcListener listener);

	void send(String resource, String action);

	void send(String resource, String action, Object value);

	void notify(String resource, String action, Object value);

	void sendMsg(I18nMessage msg, MessageState state);

	StrolchTransaction openTx(Certificate cert, boolean readOnly);
}
