package li.strolch.plc.core.hw.gpio;

import static java.util.stream.Collectors.joining;
import static li.strolch.plc.model.PlcConstants.PARAM_SIMULATED;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.connections.SimplePlcConnection;

public class RaspiBcmGpioInputConnection extends SimplePlcConnection {

	private boolean verbose;
	private List<Integer> inputBcmAddresses;
	private Map<String, Pin> pinsByAddress;
	private Map<GpioPin, String> addressesByPin;

	private boolean inverted;
	private PinPullResistance pinPullResistance;

	public RaspiBcmGpioInputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		this.simulated = parameters.containsKey(PARAM_SIMULATED) && (boolean) parameters.get(PARAM_SIMULATED);

		@SuppressWarnings("unchecked")
		List<Integer> bcmInputPins = (List<Integer>) parameters.get("bcmInputPins");
		this.inputBcmAddresses = bcmInputPins;

		this.pinsByAddress = new HashMap<>();
		for (Integer address : this.inputBcmAddresses) {
			Pin pin = RaspiBcmPin.getPinByAddress(address);
			if (pin == null)
				throw new IllegalArgumentException("RaspiBcmPin " + address + " does not exist!");
			String key = this.id + "." + address;
			this.pinsByAddress.put(key, pin);
			logger.info("Registered address " + key + " for RaspiBcmPin " + pin);
		}

		if (parameters.containsKey("pinPullResistance")) {
			this.pinPullResistance = PinPullResistance.valueOf((String) parameters.get("pinPullResistance"));
		} else {
			this.pinPullResistance = PinPullResistance.OFF;
		}

		this.verbose = parameters.containsKey("verbose") && (Boolean) parameters.get("verbose");
		this.inverted = parameters.containsKey("inverted") && (boolean) parameters.get("inverted");

		logger.info("Configured Raspi BCM GPIO Input for Pins " + this.inputBcmAddresses.stream().map(Object::toString)
				.collect(joining(", ")));
	}

	@Override
	public boolean connect() {
		if (this.simulated) {
			logger.warn(this.id + ": Running SIMULATED, NOT CONNECTING!");
			return super.connect();
		}

		try {
			GpioController gpioController = PlcGpioController.getInstance();

			this.addressesByPin = new HashMap<>();
			for (String address : this.pinsByAddress.keySet()) {
				Pin pin = this.pinsByAddress.get(address);
				if (gpioController.getProvisionedPins().stream().map(GpioPin::getPin).anyMatch(pin::equals))
					throw new IllegalStateException("Pin " + pin + " is already provisioned!");

				GpioPinDigitalInput inputPin = gpioController.provisionDigitalInputPin(pin, this.pinPullResistance);

				inputPin.removeAllListeners();
				inputPin.addListener((GpioPinListenerDigital) this::handleInterrupt);

				this.addressesByPin.put(inputPin, address);
				logger.info("Provisioned input pin  " + inputPin + " for address " + address);
			}

			return super.connect();

		} catch (Throwable e) {
			handleBrokenConnection("Failed to connect to GpioController: " + getExceptionMessageWithCauses(e), e);
			return false;
		}
	}

	private void handleInterrupt(GpioPinDigitalStateChangeEvent event) {
		if (this.verbose)
			logger.info(event.getPin() + " " + event.getState() + " " + event.getEdge());

		String address = this.addressesByPin.get(event.getPin());
		PinState state = event.getState();
		if (this.verbose)
			logger.info(address + " has new state " + state);
		notify(address, this.inverted ? state.isLow() : state.isHigh());
	}

	@Override
	public void disconnect() {
		if (this.simulated) {
			logger.warn(this.id + ": Running SIMULATED, NOT CONNECTING!");
			super.disconnect();
			return;
		}

		try {
			GpioController gpioController = PlcGpioController.getInstance();
			for (GpioPin inputPin : this.addressesByPin.keySet()) {
				gpioController.unprovisionPin(inputPin);
			}
			this.addressesByPin.clear();
		} catch (Error e) {
			logger.error("Failed to disconnect " + this.id, e);
		}

		super.disconnect();
	}

	@Override
	public void send(String address, Object value) {
		throw new UnsupportedOperationException(getClass() + " does not support output!");
	}

	@Override
	public Set<String> getAddresses() {
		return this.pinsByAddress.keySet();
	}
}
