package li.strolch.plc.core.hw.i2c;

import static li.strolch.utils.helper.ByteHelper.asBinary;
import static li.strolch.utils.helper.ByteHelper.isBitSet;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.toHexString;
import static li.strolch.utils.helper.StringHelper.toPrettyHexString;

import java.io.IOException;
import java.util.*;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.core.hw.gpio.PlcGpioController;
import li.strolch.plc.model.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PCF8574InputConnection extends PlcConnection {

	private static final Logger logger = LoggerFactory.getLogger(PCF8574InputConnection.class);

	private boolean verbose;
	private int i2cBusNr;
	private boolean inverted;

	private byte[] addresses;
	private I2CDevice[] inputDevices;
	private boolean[][] states;

	private Map<String, int[]> positionsByAddress;

	private PinPullResistance interruptResistance;
	private int interruptBcmPinAddress;
	private PinState interruptChangeState;
	private GpioPinDigitalInput interruptGpioPin;

	public PCF8574InputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {

		if (!parameters.containsKey("i2cBus"))
			throw new IllegalArgumentException("Missing param i2cBus");
		if (!parameters.containsKey("addresses"))
			throw new IllegalArgumentException("Missing param addresses");
		if (!parameters.containsKey("interruptPinPullResistance"))
			throw new IllegalArgumentException("Missing param interruptPinPullResistance");
		if (!parameters.containsKey("interruptBcmPinAddress"))
			throw new IllegalArgumentException("Missing param interruptBcmPinAddress");
		this.verbose = parameters.containsKey("verbose") && (Boolean) parameters.get("verbose");

		this.i2cBusNr = (int) parameters.get("i2cBus");
		this.inverted = parameters.containsKey("inverted") && (boolean) parameters.get("inverted");

		@SuppressWarnings("unchecked")
		List<Integer> addressList = (List<Integer>) parameters.get("addresses");
		this.addresses = new byte[addressList.size()];
		for (int i = 0; i < addressList.size(); i++) {
			this.addresses[i] = addressList.get(i).byteValue();
		}

		Map<String, int[]> positionsByAddress = new HashMap<>();
		for (int i = 0; i < this.addresses.length; i++) {
			for (int j = 0; j < 8; j++)
				positionsByAddress.put(this.id + "." + i + "." + j, new int[] { i, j });
		}
		this.positionsByAddress = Collections.unmodifiableMap(positionsByAddress);

		this.interruptResistance = PinPullResistance.valueOf((String) parameters.get("interruptPinPullResistance"));
		this.interruptBcmPinAddress = (Integer) parameters.get("interruptBcmPinAddress");
		this.interruptChangeState = PinState.valueOf((String) parameters.get("interruptChangeState"));

		logger.info("Configured PCF8574 Input on I2C addresses 0x " + toPrettyHexString(this.addresses)
				+ " on BCM Pin interrupt trigger " + this.interruptBcmPinAddress);
		if (this.verbose)
			logger.info("Verbose enabled for connection " + this.id);
	}

	@Override
	public void connect() {
		if (this.connectionState == ConnectionState.Connected) {
			logger.warn(this.id + ": Already connected");
			return;
		}

		logger.info(this.id + ": Connecting...");

		// initialize
		try {
			I2CBus i2cBus = I2CFactory.getInstance(this.i2cBusNr);

			this.inputDevices = new I2CDevice[this.addresses.length];
			for (int i = 0; i < this.addresses.length; i++) {
				this.inputDevices[i] = i2cBus.getDevice(this.addresses[i]);
				logger.info("Connected to I2C Device at address 0x" + toHexString(this.addresses[i]) + " on I2C Bus "
						+ this.i2cBusNr);
			}

		} catch (Exception e) {
			logger.error("Failed to connect to I2C Bus " + this.i2cBusNr + " and addresses 0x " + toPrettyHexString(
					this.addresses), e);

			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg =
					"Failed to connect to I2C Bus " + this.i2cBusNr + " and addresses 0x " + toPrettyHexString(
							this.addresses) + ": " + getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
			return;
		}

		if (!readInitialState()) {
			logger.error("Failed to read initial values from I2C Bus " + this.i2cBusNr + " and addresses 0x "
					+ toPrettyHexString(this.addresses));
			this.connectionState = ConnectionState.Connected;
			this.connectionStateMsg =
					"Failed to read initial values from I2C Bus " + this.i2cBusNr + " and addresses 0x "
							+ toPrettyHexString(this.addresses);
			this.plc.notifyConnectionStateChanged(this);
		}

		// register interrupt listener
		try {
			GpioController gpioController = PlcGpioController.getInstance();

			Pin interruptPin = RaspiBcmPin.getPinByAddress(this.interruptBcmPinAddress);
			if (interruptPin == null)
				throw new IllegalStateException(
						"RaspiBcmPin with address " + this.interruptBcmPinAddress + " does not exist!");

			if (gpioController.getProvisionedPins().stream().map(GpioPin::getPin).anyMatch(interruptPin::equals))
				throw new IllegalStateException("Pin " + interruptPin + " is already provisioned!");
			this.interruptGpioPin = gpioController.provisionDigitalInputPin(interruptPin, this.interruptResistance);
			this.interruptGpioPin.removeAllListeners();
			this.interruptGpioPin.addListener((GpioPinListenerDigital) this::handleInterrupt);

			logger.info("Registered GPIO interrupt handler for BCM " + interruptPin);

			logger.info(this.id + ": Is now connected.");
			this.connectionState = ConnectionState.Connected;
			this.connectionStateMsg = "-";
			this.plc.notifyConnectionStateChanged(this);

		} catch (Exception e) {
			logger.error("Failed to register GPIO listener for BCM pin " + this.interruptBcmPinAddress, e);

			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg =
					"Failed to register GPIO listener for BCM pin " + this.interruptBcmPinAddress + ": "
							+ getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
		}
	}

	@Override
	public void disconnect() {
		if (this.interruptGpioPin != null) {
			this.interruptGpioPin.removeAllListeners();
			PlcGpioController.getInstance().unprovisionPin(this.interruptGpioPin);
		}

		this.inputDevices = null;

		this.connectionState = ConnectionState.Disconnected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	private void handleInterrupt(GpioPinDigitalStateChangeEvent event) {
		if (this.verbose)
			logger.info(event.getPin() + " " + event.getState() + " " + event.getEdge());

		try {
			if (event.getState() == this.interruptChangeState)
				handleNewState();
		} catch (Exception e) {
			logger.error("Failed to read new state for " + this.id, e);
			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg = "Failed to read new state: " + getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
		}
	}

	private void handleNewState() throws IOException {

		for (int i = 0; i < this.inputDevices.length; i++) {
			I2CDevice i2CDevice = this.inputDevices[i];
			byte data = (byte) i2CDevice.read();

			if (this.verbose)
				logger.info(
						"Address 0x" + toHexString((byte) i2CDevice.getAddress()) + " has new state " + asBinary(data));

			for (int j = 0; j < 8; j++) {
				boolean newState = isBitSet(data, j);
				if (this.inverted)
					newState = !newState;

				if (this.states[i][j] != newState) {
					this.states[i][j] = newState;
					this.plc.notify(this.id + "." + i + "." + j, newState);
				}
			}
		}
	}

	private boolean readInitialState() {

		boolean ok = true;

		this.states = new boolean[this.inputDevices.length][];

		for (int i = 0; i < this.inputDevices.length; i++) {
			I2CDevice i2CDevice = this.inputDevices[i];
			try {
				byte data = (byte) i2CDevice.read();
				logger.info("Initial Value for address 0x" + toHexString(this.addresses[i]) + " is " + asBinary(data));

				this.states[i] = new boolean[8];
				for (int j = 0; j < 8; j++) {
					boolean bitSet = isBitSet(data, j);

					if (this.inverted)
						bitSet = !bitSet;

					this.states[i][j] = bitSet;
					this.plc.notify(this.id + "." + i + "." + j, this.states[i][j]);
				}
			} catch (Exception e) {
				ok = false;
				logger.error("Failed to read initial state for address 0x" + toHexString((byte) i2CDevice.getAddress()),
						e);
			}
		}

		return ok;
	}

	@Override
	public Set<String> getAddresses() {
		return new TreeSet<>(this.positionsByAddress.keySet());
	}

	@Override
	public void send(String address, Object value) {
		throw new UnsupportedOperationException(getClass() + " does not support output!");
	}

	@Override
	public String toString() {
		return this.id;
	}
}
