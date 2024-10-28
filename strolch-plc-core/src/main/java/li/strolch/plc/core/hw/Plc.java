package li.strolch.plc.core.hw;

import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.utils.ExecutorPool;

import java.util.Set;
import java.util.stream.Stream;

public interface Plc {

	void start();

	void stop();

	Stream<PlcAddressKey> getAddressKeysStream();

	Set<PlcAddressKey> getAddressKeys();

	void setGlobalListener(PlcListener listener);

	void register(PlcAddress address, PlcListener listener);

	void unregister(PlcAddress address, PlcListener listener);

	void syncNotify(String address, Object value);

	void queueNotify(String address, Object value);

	void send(PlcAddress address);

	void send(PlcAddress address, boolean catchExceptions, boolean notifyGlobalListener);

	void send(PlcAddress address, Object value);

	void send(PlcAddress address, Object value, boolean catchExceptions, boolean notifyGlobalListener);

	void addConnection(PlcConnection connection);

	PlcConnection getConnection(String id);

	PlcConnection getConnection(PlcAddress address);

	void registerNotificationMapping(PlcAddress address);

	void notifyConnectionStateChanged(PlcConnection connection);

	void setConnectionStateChangeListener(PlcConnectionStateChangeListener listener);

	void setVerbose(boolean verbose);

	ExecutorPool getExecutorPool();
}
