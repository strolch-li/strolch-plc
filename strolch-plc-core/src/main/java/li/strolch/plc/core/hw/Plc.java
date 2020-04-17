package li.strolch.plc.core.hw;

import li.strolch.plc.model.PlcAddress;
import li.strolch.utils.ExecutorPool;

public interface Plc {

	void start();

	void stop();

	void setGlobalListener(PlcListener listener);

	void register(PlcAddress address, PlcListener listener);

	void unregister(PlcAddress address, PlcListener listener);

	void syncNotify(String address, Object value);

	void queueNotify(String address, Object value);

	void send(PlcAddress address);

	void send(PlcAddress address, Object value);

	void addConnection(PlcConnection connection);

	PlcConnection getConnection(String id);

	PlcConnection getConnection(PlcAddress address);

	void registerNotificationMapping(PlcAddress address);

	void notifyConnectionStateChanged(PlcConnection connection);

	void setConnectionStateChangeListener(PlcConnectionStateChangeListener listener);

	void setVerbose(boolean verbose);

	ExecutorPool getExecutorPool();
}
