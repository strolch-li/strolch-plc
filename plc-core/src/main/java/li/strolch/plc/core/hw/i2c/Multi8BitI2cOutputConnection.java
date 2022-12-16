package li.strolch.plc.core.hw.i2c;

import static java.util.stream.Collectors.joining;
import static li.strolch.plc.model.PlcConstants.PARAM_SIMULATED;
import static li.strolch.utils.collections.CollectionsHelper.byteStream;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.toHexString;

import java.io.IOException;
import java.util.*;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.connections.SimplePlcConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Multi8BitI2cOutputConnection extends SimplePlcConnection {

	protected static final Logger logger = LoggerFactory.getLogger(Multi8BitI2cOutputConnection.class);

	protected boolean verbose;
	protected boolean resetOnConnect;
	protected int i2cBusNr;
	protected int nrOfBits;
	protected boolean inverted;
	protected boolean reversed;

	protected byte[] addresses;
	protected I2CDevice[] outputDevices;
	protected byte[] states;

	protected Map<String, int[]> positionsByAddress;

	public Multi8BitI2cOutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	public abstract String getName();

	public String getDescription() {
		return "I2C Output " + getName() + " @ " + byteStream(this.addresses).map(b -> "0x" + toHexString(b))
				.collect(joining(", "));
	}

	public String getDescription(byte address) {
		return "I2C Output " + getName() + " @ 0x" + toHexString(address);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		this.simulated = parameters.containsKey(PARAM_SIMULATED) && (boolean) parameters.get(PARAM_SIMULATED);

		if (!parameters.containsKey("i2cBus"))
			throw new IllegalArgumentException("Missing param i2cBus");
		if (!parameters.containsKey("addresses"))
			throw new IllegalArgumentException("Missing param addresses");

		this.verbose = parameters.containsKey("verbose") && (Boolean) parameters.get("verbose");
		this.resetOnConnect = parameters.containsKey("resetOnConnect") && (Boolean) parameters.get("resetOnConnect");
		this.nrOfBits = parameters.containsKey("nrOfBits") ? ((Integer) parameters.get("nrOfBits")) : 8;
		this.i2cBusNr = (int) parameters.get("i2cBus");
		this.inverted = parameters.containsKey("inverted") && (boolean) parameters.get("inverted");
		this.reversed = parameters.containsKey("reversed") && (boolean) parameters.get("reversed");

		logger.info("inverted: " + this.inverted);
		logger.info("reversed: " + this.reversed);
		logger.info("nrOfBits: " + this.nrOfBits);

		@SuppressWarnings("unchecked")
		List<Integer> addressList = (List<Integer>) parameters.get("addresses");
		this.addresses = new byte[addressList.size()];
		for (int i = 0; i < addressList.size(); i++) {
			this.addresses[i] = addressList.get(i).byteValue();
		}

		Map<String, int[]> positionsByAddress = new HashMap<>();
		for (int i = 0; i < this.addresses.length; i++) {
			for (int j = 0; j < this.nrOfBits; j++)
				positionsByAddress.put(this.id + "." + i + "." + j, new int[] { i, j });
		}
		this.positionsByAddress = Collections.unmodifiableMap(positionsByAddress);

		logger.info("Configured " + getDescription());
	}

	@Override
	public boolean connect() {
		if (this.simulated) {
			logger.warn(getName() + ": " + this.id + ": Running SIMULATED, NOT CONNECTING!");
			return super.connect();
		}

		if (isConnected()) {
			logger.warn(getName() + ": " + this.id + ": Already connected");
			return true;
		}

		logger.info(getName() + ": " + this.id + ": Connecting...");

		// initialize
		try {
			I2CBus i2cBus = I2CFactory.getInstance(this.i2cBusNr);

			this.outputDevices = new I2CDevice[this.addresses.length];
			this.states = new byte[this.addresses.length];
			byte[] bytes = this.addresses;
			for (int i = 0; i < bytes.length; i++) {
				byte address = bytes[i];
				I2CDevice i2cDev = i2cBus.getDevice(address);
				this.outputDevices[i] = i2cDev;
			}

			if (setup()) {
				logger.info("Successfully connected " + this.outputDevices.length + " devices as " + getDescription());
				return super.connect();
			}

			return false;

		} catch (Throwable e) {
			handleBrokenConnection("Failed to connect to " + getDescription() + ": " + getExceptionMessageWithCauses(e),
					e);

			return false;
		}
	}

	protected boolean setup() throws IOException {
		boolean ok = true;

		I2CDevice[] devices = this.outputDevices;
		for (int index = 0; index < devices.length; index++) {
			byte address = this.addresses[index];
			I2CDevice outputDevice = devices[index];
			ok &= setup(address, index, outputDevice);
			logger.info("Connected " + getDescription(address));
		}

		if (ok)
			return ok;

		return false;
	}

	protected abstract boolean setup(byte address, int index, I2CDevice outputDevice) throws IOException;

	@Override
	public void disconnect() {
		if (this.simulated) {
			logger.warn(this.id + ": Running SIMULATED, NOT CONNECTING!");
			super.disconnect();
			return;
		}

		this.outputDevices = null;
		this.states = null;

		super.disconnect();
	}

	@Override
	public Set<String> getAddresses() {
		return new TreeSet<>(this.positionsByAddress.keySet());
	}

	@Override
	public void send(String address, Object value) {
		if (this.simulated) {
			logger.warn(this.id + ": Running SIMULATED, NOT CONNECTING!");
			return;
		}

		assertConnected();

		int[] pos = this.positionsByAddress.get(address);
		if (pos == null)
			throw new IllegalStateException("Address " + address + " does not exist");

		int device = pos[0];
		int pin = pos[1];

		I2CDevice outputDevice = this.outputDevices[device];

		boolean high = (boolean) value;

		// see if we need to invert
		if (this.inverted)
			high = !high;

		try {
			synchronized (this) {
				setPin(device, pin, outputDevice, high);
			}
		} catch (Exception e) {
			handleBrokenConnection("Failed to write to I2C address: " + address + " at " + getDescription(
					(byte) outputDevice.getAddress()) + ": " + getExceptionMessageWithCauses(e), e);
			throw new IllegalStateException("Failed to write to I2C address " + address + " at " + getDescription(
					(byte) outputDevice.getAddress()), e);
		}
	}

	protected abstract void setPin(int device, int pin, I2CDevice outputDevice, boolean high) throws IOException;
}