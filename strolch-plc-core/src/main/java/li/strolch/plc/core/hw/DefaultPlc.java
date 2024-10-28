package li.strolch.plc.core.hw;

import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.utils.ExecutorPool;
import li.strolch.utils.collections.MapOfLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class DefaultPlc implements Plc {

	private static final Logger logger = LoggerFactory.getLogger(DefaultPlc.class);
	public static final String VIRTUAL_BOOLEAN = "VirtualBoolean";
	public static final String VIRTUAL_STRING = "VirtualString";
	public static final String VIRTUAL_INTEGER = "VirtualInteger";

	private final Map<String, PlcAddress> notificationMappings;
	private final Map<String, PlcConnection> connections;
	private final Map<String, PlcConnection> connectionsByAddress;
	private final MapOfLists<PlcAddress, PlcListener> listeners;

	private final LinkedBlockingQueue<NotificationTask> notificationTasks;

	private PlcListener globalListener;
	private PlcConnectionStateChangeListener connectionStateChangeListener;
	private boolean verbose;

	private ExecutorPool executorPool;
	private Future<?> notificationsTask;
	private boolean run;

	public DefaultPlc() {
		this.notificationMappings = new HashMap<>();
		this.listeners = new MapOfLists<>(true);
		this.connections = new HashMap<>();
		this.connectionsByAddress = new HashMap<>();
		this.notificationTasks = new LinkedBlockingQueue<>();
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
	public void notifyConnectionStateChanged(PlcConnection connection) {
		if (this.connectionStateChangeListener != null)
			this.connectionStateChangeListener.notifyStateChange(connection);
	}

	@Override
	public void setConnectionStateChangeListener(PlcConnectionStateChangeListener listener) {
		this.connectionStateChangeListener = listener;
	}

	@Override
	public Stream<PlcAddressKey> getAddressKeysStream() {
		return this.notificationMappings.values().stream().map(PlcAddress::toPlcAddressKey);
	}

	@Override
	public Set<PlcAddressKey> getAddressKeys() {
		return getAddressKeysStream().collect(toSet());
	}

	@Override
	public void register(PlcAddress address, PlcListener listener) {
		this.listeners.addElement(address, listener);
		logger.info("{}: {}", address.toKeyAddress(), listener.getClass().getSimpleName());
	}

	@Override
	public void unregister(PlcAddress address, PlcListener listener) {
		if (this.listeners.removeElement(address, listener)) {
			logger.info("{}: {}", address, listener.getClass().getName());
		} else {
			logger.warn("Listener not registered with key {}: {}", address.toKeyAddress(),
					listener.getClass().getSimpleName());
		}
	}

	@Override
	public void syncNotify(String address, Object value) {
		doNotify(address, value);
	}

	@Override
	public void queueNotify(String address, Object value) {
		this.notificationTasks.add(new NotificationTask(address, value));
	}

	private void doNotify(String address, Object value) {
		PlcAddress plcAddress = this.notificationMappings.get(address);
		if (plcAddress == null) {
			logger.warn("No mapping to PlcAddress for hwAddress {}", address);
			return;
		}

		if (plcAddress.inverted) {
			if (value instanceof Boolean)
				value = !((boolean) value);
			else
				logger.error("{} is marked as inverted, but the value is not a boolean, but a {}", plcAddress,
						value.getClass());
		}

		doNotify(plcAddress, value, true, true);
	}

	private void doNotify(PlcAddress plcAddress, Object value, boolean catchExceptions, boolean notifyGlobalListener) {

		List<PlcListener> listeners = this.listeners.getList(plcAddress);
		if (listeners == null || listeners.isEmpty()) {
			logger.warn("No listener for update {}: {}", plcAddress.toKey(), value);
		} else {
			listeners = new ArrayList<>(listeners);
			for (PlcListener listener : listeners) {
				try {
					if (this.verbose)
						logger.info("Notifying {}: {} @ {}", plcAddress.toKey(), value, listener);
					listener.handleNotification(plcAddress, value);
				} catch (Exception e) {
					if (catchExceptions) {
						logger.error("Failed to notify listener {} for address {}", listener, plcAddress, e);
					} else {
						throw e;
					}
				}
			}
		}

		if (notifyGlobalListener && this.globalListener != null)
			this.globalListener.handleNotification(plcAddress, value);
	}

	private void doNotifications() {
		logger.info("Notifications Task running...");
		while (this.run) {
			NotificationTask task = null;
			try {
				task = this.notificationTasks.take();
				doNotify(task.address, task.value);
			} catch (InterruptedException e) {
				logger.error("Interrupted!");
			} catch (Exception e) {
				if (task != null)
					logger.error("Failed to perform notification for {}: {}", task.address, task.value, e);
				else
					logger.error("Failed to get notification task", e);
			}
		}
		logger.info("Notifications Task stopped.");
	}

	@Override
	public void send(PlcAddress plcAddress) {
		send(plcAddress, true, true);
	}

	@Override
	public void send(PlcAddress plcAddress, Object value) {
		send(plcAddress, value, true, true);
	}

	@Override
	public void send(PlcAddress plcAddress, boolean catchExceptions, boolean notifyGlobalListener) {
		logger.info("Sending {}: {} (default)", plcAddress.toKey(), plcAddress.defaultValue);
		if (!isVirtual(plcAddress))
			validateConnection(plcAddress).send(plcAddress.address, plcAddress.defaultValue);
		doNotify(plcAddress, plcAddress.defaultValue, catchExceptions, notifyGlobalListener);
	}

	@Override
	public void send(PlcAddress plcAddress, Object value, boolean catchExceptions, boolean notifyGlobalListener) {
		logger.info("Sending {}: {}", plcAddress.toKey(), value);
		if (!isVirtual(plcAddress))
			validateConnection(plcAddress).send(plcAddress.address, value);
		doNotify(plcAddress, value, catchExceptions, notifyGlobalListener);
	}

	private PlcConnection validateConnection(PlcAddress plcAddress) {
		PlcConnection connection = getConnection(plcAddress);
		if (!connection.isAutoConnect() || connection.isConnected())
			return connection;

		connection.connect();

		if (connection.isConnected())
			return connection;

		throw new IllegalStateException(
				"Could not connect to " + connection.getId() + " due to " + connection.getStateMsg());
	}

	@Override
	public void addConnection(PlcConnection connection) {
		this.connections.put(connection.getId(), connection);
		Set<String> addresses = connection.getAddresses();
		logger.info("Adding connection {} {} with {} addresses...", connection.getId(), connection.getClass().getName(),
				addresses.size());
		for (String address : addresses) {
			logger.info("  Adding {}...", address);
			this.connectionsByAddress.put(address, connection);
		}
	}

	@Override
	public void start() {
		this.executorPool = new ExecutorPool();
		this.run = true;
		this.notificationsTask = this.executorPool.getSingleThreadExecutor("PlcNotify").submit(this::doNotifications);
		this.connections.values().stream().filter(PlcConnection::isAutoConnect).forEach(PlcConnection::connect);
	}

	@Override
	public void stop() {
		this.run = false;
		if (this.notificationsTask != null)
			this.notificationsTask.cancel(true);
		this.connections.values().forEach(PlcConnection::disconnect);
		if (this.executorPool != null)
			this.executorPool.destroy();
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

		logger.info("Registered {}", address);
	}

	private void validateVirtualAddress(PlcAddress address) {
		switch (address.address) {
			case VIRTUAL_BOOLEAN, VIRTUAL_BOOLEAN + ".", VIRTUAL_STRING, VIRTUAL_STRING + ".", VIRTUAL_INTEGER,
				 VIRTUAL_INTEGER + "." -> throw new IllegalStateException(
					"Virtual address " + address.address + " is missing sub component for " + address);
		}
	}

	private boolean isVirtual(PlcAddress address) {
		return address.address.startsWith(VIRTUAL_BOOLEAN) //
				|| address.address.startsWith(VIRTUAL_STRING) //
				|| address.address.startsWith(VIRTUAL_INTEGER);
	}

	@Override
	public ExecutorPool getExecutorPool() {
		return this.executorPool;
	}

	private record NotificationTask(String address, Object value) {
	}
}
