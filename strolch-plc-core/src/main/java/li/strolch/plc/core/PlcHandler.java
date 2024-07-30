package li.strolch.plc.core;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.model.log.LogMessage;
import li.strolch.model.Locator;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcListener;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;

public interface PlcHandler {

	ComponentContainer getContainer();

	String getPlcId();

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

	void send(String resource, String action, boolean catchExceptions, boolean notifyGlobalListener);

	void send(String resource, String action, Object value);

	void send(String resource, String action, Object value, boolean catchExceptions, boolean notifyGlobalListener);

	void notify(String resource, String action, Object value);

	void sendMsg(LogMessage message);

	void disableMsg(Locator locator);

	StrolchTransaction openTx(Certificate cert, boolean readOnly);
}
