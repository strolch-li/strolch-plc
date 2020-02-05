package li.strolch.plc.core.hw;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
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

	public DefaultPlc() {
		this.notificationMappings = new HashMap<>();
		this.listeners = new MapOfLists<>();
		this.connections = new HashMap<>();
		this.connectionsByAddress = new HashMap<>();
	}

	@Override
	public void setGlobalListener(PlcListener listener) {
		this.globalListener = listener;
	}

	@Override
	public void registerListener(PlcAddress address, PlcListener listener) {
		this.listeners.addElement(address, listener);
		logger.info("Registered listener " + listener + " with key " + address);
	}

	@Override
	public void unregisterListener(PlcAddress address, PlcListener listener) {
		if (this.listeners.removeElement(address, listener)) {
			logger.info("Unregistered listener " + listener + " with key " + address);
		} else {
			logger.warn("Listener " + listener + " not registered with key " + address);
		}
	}

	@Override
	public void notify(String address, Object value) {
		logger.info("Update for " + address + " with value " + value);

		PlcAddress plcAddress = this.notificationMappings.get(address);
		if (plcAddress == null) {
			logger.warn("No mapping to PlcAddress for hwAddress " + address);
			return;
		}

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
		logger.info("Sending " + plcAddress.resource + "-" + plcAddress.action + ": " + plcAddress.defaultValue
				+ " (default)");
		validateConnection(plcAddress).send(plcAddress.address, plcAddress.defaultValue);
		notify(plcAddress.address, plcAddress.defaultValue);
	}

	@Override
	public void send(PlcAddress plcAddress, Object value) {
		logger.info("Sending " + plcAddress.resource + "-" + plcAddress.action + ": " + value);
		validateConnection(plcAddress).send(plcAddress.address, value);
		notify(plcAddress.address, value);
	}

	private PlcConnection validateConnection(PlcAddress plcAddress) {
		PlcConnection connection = getConnection(plcAddress);
		if (connection.getState() == ConnectionState.Connected)
			return connection;

		connection.connect();

		if (connection.getState() == ConnectionState.Connected)
			return connection;

		throw new IllegalStateException("PlcConnection " + connection.getId() + " is disconnected for " + plcAddress);
	}

	@Override
	public void addConnection(PlcConnection connection) {
		this.connections.put(connection.getId(), connection);
		Set<String> addresses = connection.getAddresses();
		logger.info("Adding connection " + connection + " with " + addresses.size() + " addresses...");
		for (String address : addresses) {
			logger.info("  Adding address " + address + "...");
			this.connectionsByAddress.put(address, connection);
		}
	}

	@Override
	public void start() {
		this.connections.values().forEach(PlcConnection::connect);
	}

	@Override
	public void stop() {
		this.connections.values().forEach(PlcConnection::disconnect);
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
			throw new IllegalStateException("No PlcConnection exists for address " + address.address);
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
		if (!this.connectionsByAddress.containsKey(address.address))
			throw new IllegalStateException(
					"There is no connection registered for address " + address.address + " for key " + address);
		logger.info("Registered address mapping for " + address);

		if (address.type != PlcAddressType.Notification)
			throw new IllegalArgumentException("Key must be of type " + PlcAddressType.Notification + ": " + address);

		PlcAddress replaced = this.notificationMappings.put(address.address, address);
		if (replaced != null) {
			throw new IllegalArgumentException(
					"Replaced mapping for address " + address.address + " for key " + replaced + " with " + address);
		}
	}
}
