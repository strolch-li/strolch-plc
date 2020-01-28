package li.strolch.plc.core.hw.i2c;

import static li.strolch.utils.helper.ByteHelper.asBinary;
import static li.strolch.utils.helper.ByteHelper.isBitSet;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.toHexString;

import java.io.IOException;
import java.util.*;

import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.core.hw.gpio.PlcGpioController;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PCF8574InputConnection extends PlcConnection {

	private static final Logger logger = LoggerFactory.getLogger(PCF8574InputConnection.class);

	private int i2cBusNr;
	private byte address;
	private int interruptBcmPinAddress;
	private PinState interruptChangeState;

	private Map<String, Integer> positionsByAddress;
	private I2CDevice inputDev;
	private GpioPinDigitalInput interruptGpioPin;

	private boolean[] states;

	public PCF8574InputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {

		if (!parameters.containsKey("i2cBus"))
			throw new IllegalArgumentException("Missing param i2cBus");
		if (!parameters.containsKey("address"))
			throw new IllegalArgumentException("Missing param address");
		if (!parameters.containsKey("interruptBcmPinAddress"))
			throw new IllegalArgumentException("Missing param interruptBcmPinAddress");

		this.i2cBusNr = (int) parameters.get("i2cBus");
		String addressS = (String) parameters.get("address");
		this.address = Integer.decode(addressS).byteValue();
		this.interruptBcmPinAddress = (Integer) parameters.get("interruptBcmPinAddress");
		this.interruptChangeState = PinState.valueOf((String) parameters.get("interruptChangeState"));

		Map<String, Integer> positionsByAddress = new HashMap<>();
		for (int i = 0; i < 8; i++)
			positionsByAddress.put(this.id + "." + i, i);
		this.positionsByAddress = Collections.unmodifiableMap(positionsByAddress);

		logger.info("Configured PCF8574 Input on I2C address " + addressS + " on BCM Pin interrupt trigger "
				+ this.interruptBcmPinAddress);
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
			this.inputDev = i2cBus.getDevice(this.address);
			logger.info("Connected to I2C Device at address 0x" + toHexString(this.address) + " on I2C Bus "
					+ this.i2cBusNr);
		} catch (Exception e) {
			logger.error("Failed to connect to I2C Bus " + this.i2cBusNr + " and address " + toHexString(this.address),
					e);

			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg =
					"Failed to connect to I2C Bus " + this.i2cBusNr + " and address " + toHexString(this.address) + ": "
							+ getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
			return;
		}

		try {
			readInitialState();
		} catch (Exception e) {
			logger.error("Failed to read initial values from I2C Bus " + this.i2cBusNr + " and address " + toHexString(
					this.address), e);

			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg =
					"Failed to read initial values from I2C Bus " + this.i2cBusNr + " and address " + toHexString(
							this.address) + ": " + getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
			return;
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
			this.interruptGpioPin = gpioController.provisionDigitalInputPin(interruptPin);
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

		this.inputDev = null;

		this.connectionState = ConnectionState.Disconnected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	private void handleInterrupt(GpioPinDigitalStateChangeEvent event) {
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
		byte data = (byte) this.inputDev.read();
		boolean newState;

		for (int i = 0; i < this.states.length; i++) {
			newState = isBitSet(data, i);
			if (this.states[i] != newState) {
				this.states[i] = newState;
				this.plc.notify(this.id + "." + i, newState);
			}
		}
	}

	private void readInitialState() throws IOException {
		byte data = (byte) this.inputDev.read();
		logger.info("Initial Value: " + asBinary(data));

		this.states = new boolean[8];
		for (int i = 0; i < this.states.length; i++) {
			this.states[i] = isBitSet(data, i);
			this.plc.notify(this.id + "." + i, this.states[i]);
		}
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
