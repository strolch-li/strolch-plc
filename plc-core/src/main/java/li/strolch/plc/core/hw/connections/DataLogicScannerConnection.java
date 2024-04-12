package li.strolch.plc.core.hw.connections;

import li.strolch.plc.core.hw.Plc;
import li.strolch.utils.helper.AsciiHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.text.MessageFormat.format;
import static li.strolch.plc.model.PlcConstants.PARAM_SIMULATED;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;

public class DataLogicScannerConnection extends SimplePlcConnection {

	public static final String NO_CONNECTION = "*NoConnection*";
	public static final String NO_READ = "*NoRead*";

	private static final String ADDR_TRIGGER = ".trigger";
	private static final String ADDR_BARCODE = ".barcode";

	private InetAddress address;
	private int port;
	private int readTimeout;

	private Socket socket;
	private boolean triggered;
	private boolean read;
	private Future<?> readTask;
	private HashSet<String> addresses;

	private final String addressTrigger;
	private final String addressBarcode;

	public DataLogicScannerConnection(Plc plc, String id) {
		super(plc, id);
		this.addressTrigger = id + ADDR_TRIGGER;
		this.addressBarcode = id + ADDR_BARCODE;
	}

	@Override
	public boolean isAutoConnect() {
		return false;
	}

	@Override
	public void initialize(Map<String, Object> parameters) throws Exception {
		this.simulated = parameters.containsKey(PARAM_SIMULATED) && (boolean) parameters.get(PARAM_SIMULATED);

		String address = (String) parameters.get("address");
		String[] parts = address.split(":");
		this.address = Inet4Address.getByName(parts[0]);
		this.port = Integer.parseInt(parts[1]);
		this.readTimeout = (int) parameters.get("readTimeout");

		this.addresses = new HashSet<>();
		this.addresses.add(this.addressTrigger);
		this.addresses.add(this.addressBarcode);

		logger.info("Configured DataLogic Scanner connection to {}:{}", this.address, this.port);
	}

	@Override
	public boolean connect() {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return super.connect();
		}

		if (isConnected())
			return true;

		try {
			this.socket = new Socket(this.address, this.port);
			this.socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(this.readTimeout));
			logger.info("Connected DataLogic Scanner connection to {}:{}", this.address, this.port);
			this.read = true;
			this.readTask = this.plc.getExecutorPool().getSingleThreadExecutor(this.id).submit(this::read);

			return super.connect();

		} catch (IOException e) {
			handleBrokenConnection(
					"Failed to connect to " + this.address + ":" + this.port + ": " + getExceptionMessageWithCauses(e),
					e);

			return false;
		}
	}

	@Override
	public void disconnect() {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			super.disconnect();
			return;
		}

		internalDisconnect();
		super.disconnect();
	}

	private void internalDisconnect() {
		this.read = false;
		if (this.readTask != null) {
			this.readTask.cancel(true);
		}

		if (this.socket != null) {
			logger.warn("Closing socket to {}:{}", this.address, this.port);
			try {
				this.socket.shutdownInput();
				this.socket.shutdownOutput();
				this.socket.close();
			} catch (IOException e) {
				logger.error("Failed to close socket", e);
			}
			this.socket = null;
		}
	}

	@Override
	public Set<String> getAddresses() {
		return this.addresses;
	}

	private void sendStartTrigger() throws IOException {
		this.triggered = true;
		this.socket.getOutputStream().write('T');
		this.socket.getOutputStream().flush();
		logger.info("Triggered DataLogicScanner");
	}

	private void sendStopTrigger() throws IOException {
		this.triggered = false;
		this.socket.getOutputStream().write('S');
		logger.info("Stopped DataLogicScanner");
	}

	@Override
	public void send(String address, Object value) {
		if (!this.addressTrigger.equals(address))
			throw new IllegalStateException("Illegal Address " + address);

		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return;
		}

		boolean trigger = (boolean) value;

		try {
			if (trigger) {
				if (!connect())
					throw new IllegalStateException("Could not connect to " + this.address + ":" + this.port);
				sendStartTrigger();
			} else {
				if (isConnected()) {
					sendStopTrigger();
					disconnect();
				}
			}

		} catch (IOException e) {
			handleBrokenConnection(
					format("Failed to handle address {0} for {1}:{2}: {3}", address, this.address, this.port,
							getExceptionMessageWithCauses(e)), e);

			throw new IllegalStateException(
					"Failed to handle address " + address + " for " + this.address + ":" + this.port, e);
		}
	}

	private void read() {

		logger.info("Reading from DataLogic Scanner at {}:{}...", this.address, this.port);
		while (this.read) {
			try {

				InputStream inputStream = this.socket.getInputStream();

				int read;
				while ((read = inputStream.read()) != AsciiHelper.STX) {
					if (read == -1) {
						if (this.read) {
							throw new IllegalStateException("No data read from socket!");
						} else {
							logger.warn("Disconnect requested while waiting for data.");
							break;
						}
					}
				}

				if (this.read) {
					StringBuilder sb = new StringBuilder();

					while ((read = inputStream.read()) != AsciiHelper.ETX) {
						if (read == -1) {
							if (this.read) {
								throw new IllegalStateException("No data read from socket!");
							} else {
								logger.warn("Disconnected requested while waiting for data.");
								break;
							}
						}

						sb.append((char) read);
					}

					String barcode = sb.toString();
					logger.info("Received barcode {}", barcode);
					notify(this.addressBarcode, barcode);
				}

			} catch (Exception e) {
				if (e instanceof SocketTimeoutException) {
					if (this.triggered) {
						notify(this.addressBarcode, NO_READ);
						try {
							sendStopTrigger();
						} catch (IOException ex) {
							logger.error("Failed to send stop during timeout exception: {}", ex.getMessage());
						}
						internalDisconnect();
						handleBrokenConnection(
								format("Timeout while reading from scanner at {0}:{1}: {2}", this.address, this.port,
										getExceptionMessageWithCauses(e)), e);
					} else {
						logger.warn("Timeout while reading from scanner at {}:{}. Disconnected.", this.address,
								this.port);
						notify(this.addressBarcode, NO_CONNECTION);
						disconnect();
					}
				} else {
					notify(this.addressBarcode, NO_CONNECTION);
					internalDisconnect();
					handleBrokenConnection(format("Failed to connect to {0}:{1}: {2}", this.address, this.port,
							getExceptionMessageWithCauses(e)), e);
				}
			}
		}

		logger.info("Stopped reading from {}:{}", this.address, this.port);
	}
}
