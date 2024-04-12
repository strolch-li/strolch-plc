package li.strolch.plc.core.hw.i2c;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.pi4j.wiringpi.Gpio;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.connections.SimplePlcConnection;
import li.strolch.plc.core.hw.gpio.PlcGpioController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.pi4j.wiringpi.Gpio.HIGH;
import static com.pi4j.wiringpi.Gpio.LOW;
import static java.text.MessageFormat.format;
import static li.strolch.plc.model.PlcConstants.PARAM_SIMULATED;
import static li.strolch.utils.helper.ByteHelper.asBinary;
import static li.strolch.utils.helper.ByteHelper.isBitSet;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.toHexString;
import static li.strolch.utils.helper.StringHelper.toPrettyHexString;

public class PCF8574InputConnection extends SimplePlcConnection {

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
	private Future<?> interruptFixTask;
	private long lastInterrupt;
	private long interruptFixes;
	private boolean enableInterruptFix;

	public PCF8574InputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		this.simulated = parameters.containsKey(PARAM_SIMULATED) && (boolean) parameters.get(PARAM_SIMULATED);

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

		@SuppressWarnings("unchecked") List<Integer> addressList = (List<Integer>) parameters.get("addresses");
		this.addresses = new byte[addressList.size()];
		for (int i = 0; i < addressList.size(); i++) {
			this.addresses[i] = addressList.get(i).byteValue();
		}

		Map<String, int[]> positionsByAddress = new HashMap<>();
		for (int i = 0; i < this.addresses.length; i++) {
			for (int j = 0; j < 8; j++)
				positionsByAddress.put(this.id + "." + i + "." + j, new int[]{i, j});
		}
		this.positionsByAddress = Collections.unmodifiableMap(positionsByAddress);

		this.interruptResistance = PinPullResistance.valueOf((String) parameters.get("interruptPinPullResistance"));
		this.interruptBcmPinAddress = (Integer) parameters.get("interruptBcmPinAddress");
		this.interruptChangeState = PinState.valueOf((String) parameters.get("interruptChangeState"));
		this.enableInterruptFix = parameters.containsKey("enableInterruptFix") && (Boolean) parameters.get(
				"enableInterruptFix");

		logger.info("Configured {} as PCF8574 Input on I2C addresses 0x {} on BCM Pin interrupt trigger {}", this.id,
				toPrettyHexString(this.addresses), this.interruptBcmPinAddress);
		if (this.verbose)
			logger.info("Verbose enabled for connection {}", this.id);
	}

	@Override
	public boolean connect() {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return super.connect();
		}

		if (isConnected()) {
			logger.warn("{}: Already connected", this.id);
			return true;
		}

		logger.info("{}: Connecting...", this.id);

		// initialize
		try {
			I2CBus i2cBus = I2CFactory.getInstance(this.i2cBusNr);

			this.inputDevices = new I2CDevice[this.addresses.length];
			for (int i = 0; i < this.addresses.length; i++) {
				this.inputDevices[i] = i2cBus.getDevice(this.addresses[i]);
				logger.info("Connected to I2C Device {} at 0x{} on I2C Bus {}", this.id, toHexString(this.addresses[i]),
						this.i2cBusNr);
			}

		} catch (Throwable e) {
			handleBrokenConnection(
					"Failed to connect to I2C Bus " + this.i2cBusNr + " and addresses 0x " + toPrettyHexString(
							this.addresses) + ": " + getExceptionMessageWithCauses(e), e);
			return false;
		}

		boolean ok = readInitialState();
		if (!ok) {
			handleBrokenConnection(
					format("Failed to read initial values from I2C Bus {0} and addresses 0x {1}", this.i2cBusNr,
							toPrettyHexString(this.addresses)), null);
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
			logger.info("Provisioned GPIO Input pin {} with PinPullResistance {}", this.interruptGpioPin,
					this.interruptResistance);
			this.interruptGpioPin.removeAllListeners();
			this.interruptGpioPin.addListener((GpioPinListenerDigital) this::handleInterrupt);

			logger.info("Registered GPIO interrupt handler for BCM {}", interruptPin);

			if (this.enableInterruptFix) {
				this.interruptFixTask = this.plc
						.getExecutorPool()
						.getScheduledExecutor("InterruptFix")
						.scheduleWithFixedDelay(this::checkInterruptPin, 1, 1, TimeUnit.SECONDS);
				logger.info("Enabled Interrupt Fix Task.");
			}

			return ok && super.connect();

		} catch (Throwable e) {
			handleBrokenConnection(
					format("Failed to register GPIO listener for BCM pin {0}: {1}", this.interruptBcmPinAddress,
							getExceptionMessageWithCauses(e)), e);

			return false;
		}
	}

	@Override
	public void disconnect() {
		if (this.simulated) {
			super.disconnect();
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return;
		}

		if (this.interruptFixTask != null)
			this.interruptFixTask.cancel(true);

		if (this.interruptGpioPin != null) {
			try {
				this.interruptGpioPin.removeAllListeners();
				PlcGpioController.getInstance().unprovisionPin(this.interruptGpioPin);
				logger.info("Provisioned GPIO Input pin {}", this.interruptGpioPin);
			} catch (Exception e) {
				logger.error("Failed to unprovision pin {}", this.interruptGpioPin, e);
			}
		}

		this.inputDevices = null;
		super.disconnect();
	}

	private void checkInterruptPin() {

		// only if we haven't had an interrupt in a while
		if (this.lastInterrupt > System.currentTimeMillis() - 1000) {
			return;
		}

		int currentState = Gpio.digitalRead(this.interruptGpioPin.getPin().getAddress());

		if ((this.interruptChangeState == PinState.HIGH && currentState == HIGH) //
				|| (this.interruptChangeState == PinState.LOW && currentState == LOW)) {
			logger.error(
					"Missed interrupt for pin {} as current state is {} and expected change state is {}, forcing update...",
					this.interruptGpioPin, currentState, this.interruptChangeState);

			try {
				handleNewState("interruptFix");
			} catch (Exception e) {
				handleBrokenConnection("Failed to read new state: " + getExceptionMessageWithCauses(e), e);
			}

			this.interruptFixes++;
			logger.error("Performed {} interrupt fixes.", this.interruptFixes);
		}
	}

	private void handleInterrupt(GpioPinDigitalStateChangeEvent event) {
		if (this.verbose)
			logger.info("{} {} {}", event.getPin(), event.getState(), event.getEdge());

		try {
			if (event.getState() == this.interruptChangeState)
				handleNewState("interrupt");
		} catch (Exception e) {
			handleBrokenConnection("Failed to read new state: " + getExceptionMessageWithCauses(e), e);
		}
	}

	private void handleNewState(String ctx) throws IOException {

		for (int i = 0; i < this.inputDevices.length; i++) {
			I2CDevice i2CDevice = this.inputDevices[i];
			if (i2CDevice == null) {
				logger.warn("Ignoring invalid I2C Device 0x{} {}", toHexString(this.addresses[i]), ctx);
				continue;
			}

			byte data = (byte) i2CDevice.read();

			if (this.verbose)
				logger.info("{} at 0x{} has new state {} {}", this.id, toHexString((byte) i2CDevice.getAddress()),
						asBinary(data), ctx);

			for (int j = 0; j < 8; j++) {
				boolean newState = isBitSet(data, j);
				if (this.inverted)
					newState = !newState;

				if (this.states[i][j] != newState) {
					this.states[i][j] = newState;
					String address = this.id + "." + i + "." + j;
					logger.info("Detected {} = {}{}{} {}", address, newState ? 1 : 0,
							this.inverted ? " (inverted) " : " (normal) ", asBinary(data), ctx);
					this.plc.queueNotify(address, newState);
				}
			}
		}

		this.lastInterrupt = System.currentTimeMillis();
	}

	private boolean readInitialState() {

		boolean ok = true;

		this.states = new boolean[this.inputDevices.length][];

		for (int i = 0; i < this.inputDevices.length; i++) {
			I2CDevice i2CDevice = this.inputDevices[i];
			try {
				byte data = (byte) i2CDevice.read();
				logger.info("Initial Value for {} at 0x{} is {}", this.id, toHexString(this.addresses[i]),
						asBinary(data));

				this.states[i] = new boolean[8];
				for (int j = 0; j < 8; j++) {
					boolean bitSet = isBitSet(data, j);

					if (this.inverted)
						bitSet = !bitSet;

					this.states[i][j] = bitSet;
					this.plc.queueNotify(this.id + "." + i + "." + j, this.states[i][j]);
				}
			} catch (Exception e) {
				ok = false;
				this.inputDevices[i] = null;
				logger.error("Failed to read initial state for {} at 0x{}", this.id,
						toHexString((byte) i2CDevice.getAddress()), e);
			}
		}

		this.lastInterrupt = System.currentTimeMillis();
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
}
