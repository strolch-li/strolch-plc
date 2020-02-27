package li.strolch.plc.core.hw;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.utils.ExecutorPool;
import li.strolch.utils.collections.MapOfLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPlc implements Plc {

	private static final Logger logger = LoggerFactory.getLogger(DefaultPlc.class);

	private Map<String, PlcAddress> notificationMappings;
	private Map<String, PlcConnection> connections;
	private Map<String, PlcConnection> connectionsByAddress;
	private PlcListener globalListener;
	private MapOfLists<PlcAddress, PlcListener> listeners;
	private PlcConnectionStateChangeListener connectionStateChangeListener;
	private boolean verbose;
	private ExecutorPool executorPool;

	public DefaultPlc() {
		this.notificationMappings = new HashMap<>();
		this.listeners = new MapOfLists<>();
		this.connections = new HashMap<>();
		this.connectionsByAddress = new HashMap<>();
	}

	@Override
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	@Override
	public void setGlobalListener(PlcListener listener) {
		this.globalListener = listener;
	}

	@Override
	public void register(PlcAddress address, PlcListener listener) {
		this.listeners.addElement(address, listener);
		logger.info(address.toKeyAddress() + ": " + listener.getClass().getSimpleName());
	}

	@Override
	public void unregister(PlcAddress address, PlcListener listener) {
		if (this.listeners.removeElement(address, listener)) {
			logger.info(address + ": " + listener.getClass().getName());
		} else {
			logger.warn("Listener not registered with key " + address.toKeyAddress() + ": " + listener.getClass()
					.getSimpleName());
		}
	}

	@Override
	public void notify(String address, Object value) {
		notify(address, value, true);
	}

	private void notify(String address, Object value, boolean verbose) {
		PlcAddress plcAddress = this.notificationMappings.get(address);
		if (plcAddress == null) {
			logger.warn("No mapping to PlcAddress for hwAddress " + address);
			return;
		}

		if (verbose)
			logger.info("Update for {} with value {}", plcAddress.toKey(), value);

		List<PlcListener> listeners = this.listeners.getList(plcAddress);
		if (listeners == null || listeners.isEmpty()) {
			logger.warn("No listeners for key " + plcAddress);
		} else {
			for (PlcListener listener : listeners) {
				try {
					listener.handleNotification(plcAddress, value);
				} catch (Exception e) {
					logger.error("Failed to notify listener " + listener + " for address " + plcAddress, e);
				}
			}
		}

		if (this.globalListener != null)
			this.globalListener.handleNotification(plcAddress, value);
	}

	@Override
	public void send(PlcAddress plcAddress) {
		logger.info("Sending {}: {} (default)", plcAddress.toKey(), plcAddress.defaultValue);
		if (!isVirtual(plcAddress))
			validateConnection(plcAddress).send(plcAddress.address, plcAddress.defaultValue);
		notify(plcAddress.address, plcAddress.defaultValue, false);
	}

	@Override
	public void send(PlcAddress plcAddress, Object value) {
		logger.info("Sending {}: {}", plcAddress.toKey(), value);
		if (!isVirtual(plcAddress))
			validateConnection(plcAddress).send(plcAddress.address, value);
		notify(plcAddress.address, value, false);
	}

	private PlcConnection validateConnection(PlcAddress plcAddress) {
		PlcConnection connection = getConnection(plcAddress);
		if (!connection.isAutoConnect() || connection.isConnected())
			return connection;

		connection.connect();

		if (connection.isConnected())
			return connection;

		throw new IllegalStateException(
				"PlcConnection " + connection.getId() + " could not connect to " + plcAddress + " due to " + connection
						.getStateMsg());
	}

	@Override
	public void addConnection(PlcConnection connection) {
		this.connections.put(connection.getId(), connection);
		Set<String> addresses = connection.getAddresses();
		logger.info(
				"Adding connection " + connection.getId() + " " + connection.getClass().getName() + " with " + addresses
						.size() + " addresses...");
		for (String address : addresses) {
			logger.info("  Adding " + address + "...");
			this.connectionsByAddress.put(address, connection);
		}
	}

	@Override
	public void start() {
		this.executorPool = new ExecutorPool();
		this.connections.values().stream().filter(PlcConnection::isAutoConnect).forEach(PlcConnection::connect);
	}

	@Override
	public void stop() {
		this.connections.values().forEach(PlcConnection::disconnect);
		if (this.executorPool != null)
			this.executorPool.destroy();
	}

	@Override
	public void notifyConnectionStateChanged(PlcConnection connection) {
		if (this.connectionStateChangeListener != null)
			this.connectionStateChangeListener.notifyStateChange(connection);
	}

	public void setConnectionStateChangeListener(PlcConnectionStateChangeListener listener) {
		this.connectionStateChangeListener = listener;
	}

	@Override
	public PlcConnection getConnection(PlcAddress address) {
		PlcConnection plcConnection = this.connectionsByAddress.get(address.address);
		if (plcConnection == null)
			throw new IllegalStateException("No PlcConnection exists for " + address.toKeyAddress());
		return plcConnection;
	}

	@Override
	public PlcConnection getConnection(String id) {
		PlcConnection plcConnection = this.connections.get(id);
		if (plcConnection == null)
			throw new IllegalStateException("No PlcConnection exists with id " + id);
		return plcConnection;
	}

	@Override
	public void registerNotificationMapping(PlcAddress address) {

		boolean virtual = isVirtual(address);
		if (virtual)
			validateVirtualAddress(address);
		else if (!this.connectionsByAddress.containsKey(address.address))
			throw new IllegalStateException("No PlcConnection exists for " + address.toKeyAddress());

		if (address.type != PlcAddressType.Notification)
			throw new IllegalArgumentException("Key must be of type " + PlcAddressType.Notification + ": " + address);

		PlcAddress replaced = this.notificationMappings.put(address.address, address);
		if (replaced != null)
			throw new IllegalArgumentException(
					"Replaced mapping for address " + address.address + " for key " + replaced + " with " + address);

		logger.info("Registered " + address);
	}

	private void validateVirtualAddress(PlcAddress address) {

		if (address.address.equals("virtualBoolean") || address.address.equals("virtualBoolean.")) {
			throw new IllegalStateException(
					"Virtual address " + address.address + " is missing sub component for " + address);
		}

		if (address.address.equals("virtualString") || address.address.equals("virtualString.")) {
			throw new IllegalStateException(
					"Virtual address " + address.address + " is missing sub component for " + address);
		}
	}

	private boolean isVirtual(PlcAddress address) {
		return address.address.startsWith("virtualBoolean") //
				|| address.address.startsWith("virtualString");
	}

	@Override
	public ExecutorPool getExecutorPool() {
		return this.executorPool;
	}
}
