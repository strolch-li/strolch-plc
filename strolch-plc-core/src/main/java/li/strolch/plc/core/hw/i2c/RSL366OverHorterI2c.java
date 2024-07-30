package li.strolch.plc.core.hw.i2c;

import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.io.i2c.impl.I2CBusImpl;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.connections.SimplePlcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import static java.text.MessageFormat.format;
import static li.strolch.plc.model.PlcConstants.PARAM_SIMULATED;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.toHexString;

public class RSL366OverHorterI2c extends SimplePlcConnection {

	// https://www.horter.de/doku/i2c-hs-433MHz_Beschreibung.pdf

	static final byte CONF_PROTOCOL = 0x02;

	static final byte ADDR_REG_SYS_CODE = 0x00;
	static final byte ADDR_REG_DEV_CODE = 0x01;
	static final byte ADDR_REG_CONF_CODE = 0x02;

	static final byte LEN_SYS = 5;
	static final byte LEN_DEV = 8;
	static final byte LEN_CONF = 8;

	static final byte ADDR_INFO_PTR = 0;
	static final byte ADDR_INFO_STATUS = 1;
	static final byte ADDR_INFO_TRANSMITTING = 2;
	static final byte ADDR_INFO_PROTOCOL = 3;
	static final byte ADDR_INFO_REPEATS = 4;
	static final byte ADDR_INFO_VER_MAJOR = 5;
	static final byte ADDR_INFO_VER_MINOR = 6;
	static final byte ADDR_INFO_NR_OF_KNOWN_PROTOCOLS = 7;

	static final byte TX_STATUS_OFF = 0x0F;
	static final byte TX_STATUS_ACTIVE = (byte) 0xAC;

	static final byte STATUS_OK = 0x00;
	static final byte STATUS_SYS_TOO_MUCH_DATA = 0x01;
	static final byte STATUS_SYS_MISSING_DATA = 0x02;
	static final byte STATUS_SYS_INVALID_DATA = 0x03;
	static final byte STATUS_SYS_MISSING = 0x04;
	static final byte STATUS_DEV_TOO_MUCH_DATA = 0x05;
	static final byte STATUS_DEV_INVALID_DATA = 0x06;
	static final byte STATUS_PROTO_UNKNOWN = 0x07;
	static final byte STATUS_BAD_PTR = 0x08;
	static final byte STATUS_CONF_TOO_MUCH_DATA = 0x09;

	private static final Logger logger = LoggerFactory.getLogger(RSL366OverHorterI2c.class);

	private boolean verbose;
	private int i2cBusNr;

	private I2CBusImpl i2cBus;
	private LoggingI2cDevice dev;
	private byte repeats;
	private Map<String, byte[]> positionsByAddress;
	private byte address;

	public RSL366OverHorterI2c(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		this.simulated = parameters.containsKey(PARAM_SIMULATED) && (boolean) parameters.get(PARAM_SIMULATED);

		if (!parameters.containsKey("i2cBus"))
			throw new IllegalArgumentException("Missing param i2cBus");
		if (!parameters.containsKey("address"))
			throw new IllegalArgumentException("Missing param address");

		this.i2cBusNr = (int) parameters.get("i2cBus");
		this.address = ((Integer) parameters.get("address")).byteValue();
		this.verbose = (boolean) parameters.getOrDefault("verbose", false);
		this.repeats = ((Integer) parameters.getOrDefault("repeats", 1)).byteValue();

		Map<String, byte[]> positionsByAddress = new HashMap<>();
		for (byte i = 1; i < 5; i++) {
			for (byte j = 1; j < 5; j++)
				positionsByAddress.put(this.id + "." + i + "." + j, new byte[]{i, j});
		}
		this.positionsByAddress = Collections.unmodifiableMap(positionsByAddress);

		logger.info("Configured RSL366 over Horter I2c on address 0x{}", toHexString(this.address));
	}

	public <T> T runBusLockedDeviceAction(final Callable<T> action) throws IOException {
		return this.i2cBus.runBusLockedDeviceAction(this.dev.getI2cDevice(), action);
	}

	@Override
	public synchronized boolean connect() {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return super.connect();
		}

		if (isConnected()) {
			logger.warn("{}: Already connected", this.id);
			return true;
		}

		logger.info("{}: Connecting...", this.id);

		try {
			if (this.i2cBus == null) {
				this.i2cBus = (I2CBusImpl) I2CFactory.getInstance(this.i2cBusNr);
				this.dev = new LoggingI2cDevice(i2cBus.getDevice(this.address), null);
				this.dev.setIoWait(0L, 0);
			}

			byte[] status = runBusLockedDeviceAction(this::configure);

			String version = status[ADDR_INFO_VER_MAJOR] + "." + status[ADDR_INFO_VER_MINOR];
			logger.info("Connected to 433MHz RSL366 over HorterI2C version {} supporting {} protocols", version,
					status[ADDR_INFO_NR_OF_KNOWN_PROTOCOLS]);

			logger.info("Connected to I2C device at address 0x{} on I2C Bus {}", toHexString(this.address),
					this.i2cBusNr);

			return super.connect();

		} catch (Throwable e) {
			handleBrokenConnection(
					format("Failed to connect to 433MHz RSL366 over HorterI2C at address 0x{0} on I2C Bus {1}: {2}",
							toHexString(this.address), this.i2cBusNr, getExceptionMessageWithCauses(e)), e);

			return false;
		}
	}

	@Override
	public Set<String> getAddresses() {
		return new TreeSet<>(this.positionsByAddress.keySet());
	}

	@Override
	public synchronized void send(String address, Object value) {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return;
		}

		byte[] pos = this.positionsByAddress.get(address);
		if (pos == null)
			throw new IllegalStateException("Address is illegal " + address);

		byte system = pos[0];
		byte device = pos[1];
		boolean on = (boolean) value;

		try {
			runBusLockedDeviceAction(() -> {
				assertConnected();
				setState(system, device, on);
				return null;
			});
		} catch (Exception e) {
			if (e instanceof IllegalStateException)
				throw (IllegalStateException) e;
			String msg = format("Failed to send {0} to system {1} device {2} at address 0x{3} on I2C Bus {4}",
					on ? "on" : "off", system, device, toHexString(this.address), this.i2cBusNr);
			handleBrokenConnection(msg + ": " + getExceptionMessageWithCauses(e), e);
			throw new IllegalStateException(msg, e);
		}
	}

	private byte[] configure() throws IOException, InterruptedException {

		logger.info("Configuring...");
		byte[] data = {CONF_PROTOCOL, repeats};
		this.dev.write(this.verbose, ADDR_REG_CONF_CODE, data);
		Thread.sleep(20L);

		// validate configuration
		byte[] status = readInfo(true);
		byte errorStatus = status[ADDR_INFO_STATUS];
		if (errorStatus != STATUS_OK)
			throw new IllegalStateException(
					"Device error after configure: " + errorStatus + " " + parseStatus(errorStatus));

		if (status[ADDR_INFO_PROTOCOL] != CONF_PROTOCOL)
			throw new IllegalStateException("Protocol could not be set to " + CONF_PROTOCOL);
		if (status[ADDR_INFO_REPEATS] != repeats)
			throw new IllegalStateException("Repeats could not bet set to " + repeats);

		logger.info("Configured with protocol " + CONF_PROTOCOL + " and {} repeats.", repeats);
		return status;
	}

	private void setState(byte system, byte device, boolean state) throws Exception {

		logger.info("System: {}", toHexString(system));
		logger.info("Device: {}", toHexString(device));

		byte[] status = readInfo(false);
		if (isDeviceTransmitting(status)) {
			Thread.sleep(50L);
			waitForDeviceIdle();
		}

		configure();

		// write system code
		logger.info("Writing system code...");
		this.dev.write(this.verbose, ADDR_REG_SYS_CODE, system);
		Thread.sleep(20L);
		status = readInfo(false);
		if (isSystemCodeInvalid(status))
			throw new IllegalStateException(
					"SystemCode is invalid after sending systemCode: " + parseStatus(status[ADDR_INFO_STATUS]));

		// write value code
		logger.info("Writing value code...");
		byte value = state ? (byte) (device + 128) : device;
		this.dev.write(this.verbose, ADDR_REG_DEV_CODE, value);
		Thread.sleep(50L);
		status = readInfo(false);
		if (isDeviceCodeInvalid(status))
			throw new IllegalStateException(
					"DeviceCode is invalid after sending deviceCode: " + parseStatus(status[ADDR_INFO_STATUS]));

		if (isDeviceTransmitting(status)) {
			Thread.sleep(50L);
			waitForDeviceIdle();
		}

		showInfoRegister(status);
		logger.info("Successfully sent state change to {} for device {}, {}", state ? "on" : "off", system, device);
	}

	private void waitForDeviceIdle() throws Exception {
		byte[] status = readInfo(this.verbose);
		while (isDeviceTransmitting(status)) {
			logger.info("Device is transmitting, waiting...");
			Thread.sleep(50L);
			this.dev.read(false, ADDR_REG_CONF_CODE, status);
		}
	}

	private byte[] readInfo(boolean showInfoRegister) throws IOException {
		byte[] status = new byte[LEN_CONF];
		this.dev.read(this.verbose, ADDR_REG_CONF_CODE, status);
		if (showInfoRegister)
			showInfoRegister(status);
		return status;
	}

	private static boolean isSystemCodeInvalid(byte[] status) {
		byte error = status[ADDR_INFO_STATUS];
		return error == STATUS_SYS_INVALID_DATA //
				|| error == STATUS_SYS_MISSING //
				|| error == STATUS_SYS_MISSING_DATA //
				|| error == STATUS_SYS_TOO_MUCH_DATA;
	}

	private static boolean isDeviceCodeInvalid(byte[] status) {
		byte error = status[ADDR_INFO_STATUS];
		return error == STATUS_DEV_INVALID_DATA //
				|| error == STATUS_DEV_TOO_MUCH_DATA;
	}

	private static void showInfoRegister(byte[] status) {
		logger.info("    Pointer  : {}", toHexString(status[ADDR_INFO_PTR]));
		logger.info("    Status   : {} {}", toHexString(status[ADDR_INFO_STATUS]),
				parseStatus(status[ADDR_INFO_STATUS]));
		logger.info("    TX       : {}", toHexString(status[ADDR_INFO_TRANSMITTING]));
		logger.info("    Protocol : {}", toHexString(status[ADDR_INFO_PROTOCOL]));
		logger.info("    Repeats  : {}", toHexString(status[ADDR_INFO_REPEATS]));
	}

	private static boolean isDeviceTransmitting(byte[] status) {
		return status[ADDR_INFO_TRANSMITTING] == TX_STATUS_ACTIVE;
	}

	private static String parseStatus(byte status) {
		return switch (status) {
			case STATUS_OK -> "OK";
			case STATUS_SYS_TOO_MUCH_DATA -> "Too much SystemCode data";
			case STATUS_SYS_MISSING_DATA -> "SystemCode missing data";
			case STATUS_SYS_INVALID_DATA -> "Invalid SystemCode";
			case STATUS_SYS_MISSING -> "SystemCode Missing";
			case STATUS_DEV_TOO_MUCH_DATA -> "Too much device data";
			case STATUS_DEV_INVALID_DATA -> "DeviceCode invalid";
			case STATUS_PROTO_UNKNOWN -> "Invalid protocol";
			case STATUS_BAD_PTR -> "Bad pointer";
			case STATUS_CONF_TOO_MUCH_DATA -> "Too much config data";
			default -> "Unknown status " + toHexString(status);
		};
	}
}
