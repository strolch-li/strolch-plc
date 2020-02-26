package li.strolch.plc.core.hw.connections;

import static java.util.Collections.singletonList;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.isEmpty;

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

import li.strolch.plc.core.hw.Plc;
import li.strolch.utils.helper.AsciiHelper;

public class DataLogicScannerConnection extends SimplePlcConnection {

	public static final String NO_CONNECTION = "*NoConnection*";
	public static final String NO_READ = "*NoRead*";
	public static final String CMD_START = "Start";
	public static final String CMD_STOP = "Stop";
	private InetAddress address;
	private int port;
	private int readTimeout;

	private Socket socket;
	private boolean triggered;
	private boolean read;
	private Future<?> readTask;

	public DataLogicScannerConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public boolean isAutoConnect() {
		return false;
	}

	@Override
	public void initialize(Map<String, Object> parameters) throws Exception {

		String address = (String) parameters.get("address");
		String[] parts = address.split(":");
		this.address = Inet4Address.getByName(parts[0]);
		this.port = Integer.parseInt(parts[1]);
		this.readTimeout = (int) parameters.get("readTimeout");

		logger.info("Configured DataLogic Scanner connection to " + this.address + ":" + this.port);
	}

	@Override
	public boolean connect() {
		if (isConnected())
			return true;

		try {
			this.socket = new Socket(this.address, this.port);
			this.socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(this.readTimeout));
			logger.info("Connected DataLogic Scanner connection to " + this.address + ":" + this.port);
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
		internalDisconnect();
		super.disconnect();
	}

	private void internalDisconnect() {
		this.read = false;
		if (this.readTask != null) {
			logger.warn("Cancelling read task...");
			this.readTask.cancel(true);
		}

		if (this.socket != null) {
			logger.warn("Closing socket to " + this.address + ":" + this.port);
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

	private void sendStartTrigger() throws IOException {
		this.triggered = true;
		this.socket.getOutputStream().write('T');
	}

	private void sendStopTrigger() throws IOException {
		this.triggered = false;
		this.socket.getOutputStream().write('S');
	}

	private void read() {

		logger.info("Reading from DataLogic Scanner at " + this.address + ":" + this.port + "...");
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
					logger.info("Received barcode " + barcode);
					notify(this.id, barcode);
				}

			} catch (Exception e) {
				if (e instanceof SocketTimeoutException) {
					if (this.triggered) {
						notify(this.id, NO_READ);
						try {
							sendStopTrigger();
						} catch (IOException ex) {
							logger.error("Failed to send stop during timeout exception: " + ex.getMessage());
						}
						internalDisconnect();
						handleBrokenConnection(
								"Timeout while reading from scanner at " + this.address + ":" + this.port + ": "
										+ getExceptionMessageWithCauses(e), e);
					} else {
						logger.warn("Timeout while reading from scanner at " + this.address + ":" + this.port
								+ ". Disconnected.");
						notify(this.id, NO_CONNECTION);
						disconnect();
					}
				} else {
					notify(this.id, NO_CONNECTION);
					internalDisconnect();
					handleBrokenConnection("Failed to connect to " + this.address + ":" + this.port + ": "
							+ getExceptionMessageWithCauses(e), e);
				}
			}
		}

		logger.info("Stopped reading from " + this.address + ":" + this.port);
	}

	@Override
	public void send(String address, Object value) {

		String command = (String) value;
		if (isEmpty(command))
			throw new IllegalArgumentException(
					"PlcAddress " + address + " command empty. Must be one of " + CMD_START + " or " + CMD_STOP);

		try {
			switch (command) {

			case CMD_START:

				if (!connect())
					throw new IllegalStateException("Could not connect to " + this.address + ":" + this.port);
				sendStartTrigger();

				break;

			case CMD_STOP:
				if (isConnected()) {
					sendStopTrigger();
					disconnect();
				}
				break;

			default:
				throw new IllegalStateException(
						"Unhandled command " + command + ". Must be one of " + CMD_START + " or " + CMD_STOP);
			}

		} catch (IOException e) {
			handleBrokenConnection("Failed to send command " + command + " to " + this.address + ":" + this.port + ": "
					+ getExceptionMessageWithCauses(e), e);

			throw new IllegalStateException(
					"Failed to send command " + command + " to " + this.address + ":" + this.port, e);
		}
	}

	@Override
	public Set<String> getAddresses() {
		return new HashSet<>(singletonList(this.id));
	}
}
