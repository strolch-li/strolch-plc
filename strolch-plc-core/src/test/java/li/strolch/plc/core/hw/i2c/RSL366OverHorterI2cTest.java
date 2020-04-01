package li.strolch.plc.core.hw.i2c;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

/**
 * <p>Compile:</p>
 * <code>javac -cp pi4j-core-1.4-SNAPSHOT.jar:. RSL366OverHorterI2cTest.java</code>
 *
 * <p>Run:</p>
 * <code>java --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -cp pi4j-core-1.4-SNAPSHOT.jar:.
 * RSL366OverHorterI2cTest</code>
 */
public class RSL366OverHorterI2cTest {

	static final byte ADDR_REG_SYS_CODE = 0x00;
	static final byte ADDR_REG_DEV_CODE = 0x01;
	static final byte ADDR_REG_CONF_CODE = 0x02;

	static final byte LEN_SYS = 6;
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

	static byte[] systemValues = new byte[] { 0, 0, 0, 0 };

	static byte system;
	static byte device;

	static I2CDevice dev;

	public static void main(String[] args) throws Exception {
		System.out.println();

		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

		System.out.println("Getting I2C device...");
		I2CBus i2cBus = I2CFactory.getInstance(1);
		dev = i2cBus.getDevice(0x18);

		byte[] status = configure();

		String version = status[ADDR_INFO_VER_MAJOR] + "." + status[ADDR_INFO_VER_MINOR];
		System.out.println("Connected to Horter I2C to 433MHz version " + version + " supporting "
				+ status[ADDR_INFO_NR_OF_KNOWN_PROTOCOLS] + " 433MHz protocols");

		System.out.println();
		readCodes(input);
		System.out.println();

		boolean run = true;
		while (run) {
			try {

				System.out.println("Selected: " + system + "." + device);
				System.out.print("Action [o|f|c|e|x]: ");
				String action = input.readLine();

				switch (action) {
				case "o":
					setState(system, device, true);
					break;
				case "f":
					setState(system, device, false);
					break;
				case "c":
					configure();
					break;
				case "e":
					readCodes(input);
					break;
				case "x":
					run = false;
					break;
				}

			} catch (Exception e) {
				System.err.println("Error: " + getExceptionMessageWithCauses(e, true));
				e.printStackTrace();
			}
		}
	}

	private static byte[] configure() throws IOException, InterruptedException {

		// configure
		byte protocol = 2;
		byte repeats = 1;
		System.out.println("Configuring...");
		byte[] data = { protocol, repeats };
		System.out.println("=> " + toHexString(ADDR_REG_CONF_CODE) + " " + toHexString(data));
		dev.write(ADDR_REG_CONF_CODE, data);
		Thread.sleep(50L);

		// validate configuration
		byte[] status = readInfo(true);
		if (status[ADDR_INFO_PROTOCOL] != protocol)
			throw new IllegalStateException("Protocol could not be set to " + protocol);
		if (status[ADDR_INFO_REPEATS] != repeats)
			throw new IllegalStateException("Repeats could not bet set to " + repeats);
		return status;
	}

	private static void setState(byte system, byte device, boolean state) throws Exception {

		System.out.println("System: " + system);
		System.out.println("Device: " + device);

		byte[] status = readInfo(false);
		if (isDeviceTransmitting(status)) {
			Thread.sleep(100L);
			waitForDeviceIdle();
		}

		configure();

		// write system code
		System.out.println("Writing system code...");
		System.out.println("=> " + toHexString(ADDR_REG_SYS_CODE) + " " + toHexString(system));
		dev.write(ADDR_REG_SYS_CODE, system);
		Thread.sleep(5L);
		status = readInfo(true);
		if (isSystemCodeInvalid(status))
			throw new IllegalStateException(
					"SystemCode is invalid after sending systemCode: " + parseStatus(status[ADDR_INFO_STATUS]));

		// write value code
		byte value = state ? (byte) (device + 128) : device;
		System.out.println("Writing value code...");
		System.out.println("=> " + toHexString(ADDR_REG_DEV_CODE) + " " + toHexString(value));
		dev.write(ADDR_REG_DEV_CODE, value);
		Thread.sleep(5L);
		status = readInfo(false);
		if (isDeviceCodeInvalid(status))
			throw new IllegalStateException(
					"DeviceCode is invalid after sending deviceCode: " + parseStatus(status[ADDR_INFO_STATUS]));
		if (!isDeviceTransmitting(status))
			throw new IllegalStateException(
					"Device is not transmitting after sending " + toHexString(system) + "." + toHexString(value)
							+ "...");

		showInfoRegister(status);
		System.out.println(
				"Successfully sent state change to " + (state ? "on" : "off") + " for device " + system + ", "
						+ device);
	}

	private static void waitForDeviceIdle() throws Exception {
		byte[] status = readInfo(false);

		while (isDeviceTransmitting(status)) {
			System.out.println("Device is transmitting, waiting...");
			Thread.sleep(100L);

			dev.read(ADDR_REG_CONF_CODE, status, 0, status.length);
		}

		byte errorStatus = status[ADDR_INFO_STATUS];
		if (errorStatus != STATUS_OK)
			throw new IllegalStateException("Device error: " + errorStatus + " " + parseStatus(errorStatus));
	}

	private static byte[] readInfo(boolean showInfoRegister) throws IOException {
		byte[] status = new byte[LEN_CONF];
		dev.read(ADDR_REG_CONF_CODE, status, 0, status.length);
		System.out.println("<= " + toHexString(ADDR_REG_CONF_CODE) + " " + toHexString(status));
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
		System.out.println("    Pointer             : " + toHexString(status[ADDR_INFO_PTR]));
		System.out.println("    Status              : " + toHexString(status[ADDR_INFO_STATUS]) + " " + parseStatus(
				status[ADDR_INFO_STATUS]));
		System.out.println("    TX                  : " + toHexString(status[ADDR_INFO_TRANSMITTING]));
		System.out.println("    Protocol            : " + toHexString(status[ADDR_INFO_PROTOCOL]));
		System.out.println("    Repeats             : " + toHexString(status[ADDR_INFO_REPEATS]));
		System.out.println(
				"    Version             : " + status[ADDR_INFO_VER_MAJOR] + "." + status[ADDR_INFO_VER_MINOR]);
		System.out.println("    Supported Protocols : " + status[ADDR_INFO_NR_OF_KNOWN_PROTOCOLS]);
	}

	private static boolean isDeviceTransmitting(byte[] status) {
		return status[ADDR_INFO_TRANSMITTING] == TX_STATUS_ACTIVE;
	}

	private static String parseStatus(byte status) {
		switch (status) {
		case STATUS_OK:
			return "OK";
		case STATUS_SYS_TOO_MUCH_DATA:
			return "Too much SystemCode data";
		case STATUS_SYS_MISSING_DATA:
			return "SystemCode missing data";
		case STATUS_SYS_INVALID_DATA:
			return "Invalid SystemCode";
		case STATUS_SYS_MISSING:
			return "SystemCode Missing";
		case STATUS_DEV_TOO_MUCH_DATA:
			return "Too much device data";
		case STATUS_DEV_INVALID_DATA:
			return "DeviceCode invalid";
		case STATUS_PROTO_UNKNOWN:
			return "Invalid protocol";
		case STATUS_BAD_PTR:
			return "Bad pointer";
		case STATUS_CONF_TOO_MUCH_DATA:
			return "Too much config data";
		default:
			return "Unknown status " + toHexString(status);
		}
	}

	private static void readCodes(BufferedReader input) {
		boolean notRead = true;
		while (notRead) {
			try {
				System.out.print("System Code: ");
				String systemCode = input.readLine();
				system = Byte.decode(systemCode);
				if (system < 1 || system > 4)
					throw new IllegalStateException("System must be between 1 and 4 incl.");

				System.out.print("Device Code: ");
				String deviceCode = input.readLine();
				device = Byte.decode(deviceCode);
				if (device < 1 || device > 4)
					throw new IllegalStateException("Device must be between 1 and 4 incl.");

				notRead = false;

			} catch (Exception e) {
				System.err.println("Error: " + getExceptionMessageWithCauses(e, true));
			}
		}
	}

	public static String toHexString(byte[] raw) throws RuntimeException {
		return toHexString(raw, 0, raw.length);
	}

	public static String toHexString(byte data) {
		return String.format("%02x", data);
	}

	public static String toHexString(byte[] raw, int offset, int length) throws RuntimeException {
		try {
			byte[] hex = new byte[2 * length];
			int index = 0;

			int pos = offset;
			for (int i = 0; i < length; i++) {
				byte b = raw[pos];
				int v = b & 0xFF;
				hex[index++] = HEX_CHAR_TABLE[v >>> 4];
				hex[index++] = HEX_CHAR_TABLE[v & 0xF];
				pos++;
			}

			return new String(hex, "ASCII"); //$NON-NLS-1$

		} catch (UnsupportedEncodingException e) {
			String msg = MessageFormat
					.format("Something went wrong while converting to HEX: {0}", e.getMessage()); //$NON-NLS-1$
			throw new RuntimeException(msg, e);
		}
	}

	private static final byte[] HEX_CHAR_TABLE = { (byte) '0',
			(byte) '1',
			(byte) '2',
			(byte) '3',
			(byte) '4',
			(byte) '5',
			(byte) '6',
			(byte) '7',
			(byte) '8',
			(byte) '9',
			(byte) 'a',
			(byte) 'b',
			(byte) 'c',
			(byte) 'd',
			(byte) 'e',
			(byte) 'f' };

	public static String asBinary(byte b) {

		StringBuilder sb = new StringBuilder();

		sb.append(((b >>> 7) & 1));
		sb.append(((b >>> 6) & 1));
		sb.append(((b >>> 5) & 1));
		sb.append(((b >>> 4) & 1));
		sb.append(((b >>> 3) & 1));
		sb.append(((b >>> 2) & 1));
		sb.append(((b >>> 1) & 1));
		sb.append(((b >>> 0) & 1));

		return sb.toString();
	}

	public static boolean isBitSet(byte data, int position) {
		if (position > 7)
			throw new IllegalStateException("Position " + position + " is not available in a byte!");
		return ((data >> position) & 1) == 1;
	}

	public static boolean isEmpty(String value) {
		return value == null || value.isEmpty();
	}

	public static String getExceptionMessage(Throwable t, boolean withClassName) {
		if (withClassName || isEmpty(t.getMessage()))
			return t.getClass().getName() + ": " + t.getMessage();
		return t.getMessage();
	}

	public static String getExceptionMessageWithCauses(Throwable t, boolean withClassName) {
		if (t.getCause() == null)
			return getExceptionMessage(t, withClassName);

		String root = getExceptionMessageWithCauses(t.getCause(), withClassName);
		return getExceptionMessage(t, withClassName) + "\n" + root;
	}
}
