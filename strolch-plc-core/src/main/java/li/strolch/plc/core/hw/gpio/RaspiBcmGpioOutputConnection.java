package li.strolch.plc.core.hw.gpio;

import com.pi4j.io.gpio.*;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.connections.SimplePlcConnection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static li.strolch.plc.model.PlcConstants.PARAM_SIMULATED;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;

public class RaspiBcmGpioOutputConnection extends SimplePlcConnection {

	private boolean verbose;
	private Map<String, Pin> pinsByAddress;
	private Map<String, GpioPinDigitalOutput> gpioPinsByAddress;
	private boolean inverted;

	public RaspiBcmGpioOutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		this.simulated = parameters.containsKey(PARAM_SIMULATED) && (boolean) parameters.get(PARAM_SIMULATED);

		@SuppressWarnings("unchecked") List<Integer> bcmOutputPins = (List<Integer>) parameters.get("bcmOutputPins");

		this.pinsByAddress = new HashMap<>();
		for (Integer address : bcmOutputPins) {
			Pin pin = RaspiBcmPin.getPinByAddress(address);
			if (pin == null)
				throw new IllegalArgumentException("RaspiBcmPin " + address + " does not exist!");
			String key = this.id + "." + address;
			this.pinsByAddress.put(key, pin);
			logger.info("Registered address {} for RaspiBcmPin {}", key, pin);
		}

		this.verbose = parameters.containsKey("verbose") && (Boolean) parameters.get("verbose");
		this.inverted = parameters.containsKey("inverted") && (boolean) parameters.get("inverted");
		logger.info("Configured Raspi BCM GPIO Output for Pins {}",
				bcmOutputPins.stream().map(Object::toString).collect(joining(", ")));
	}

	@Override
	public boolean connect() {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return super.connect();
		}

		try {
			GpioController gpioController = PlcGpioController.getInstance();

			this.gpioPinsByAddress = new HashMap<>();
			for (String address : this.pinsByAddress.keySet()) {
				Pin pin = this.pinsByAddress.get(address);
				GpioPinDigitalOutput outputPin = gpioController.provisionDigitalOutputPin(pin);
				this.gpioPinsByAddress.put(address, outputPin);
				logger.info("Provisioned output pin  {} for address {}", outputPin, address);
			}

			return super.connect();

		} catch (Throwable e) {
			handleBrokenConnection("Failed to connect to GpioController: " + getExceptionMessageWithCauses(e), e);
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

		try {
			GpioController gpioController = PlcGpioController.getInstance();
			for (GpioPinDigitalOutput outputPin : this.gpioPinsByAddress.values()) {
				gpioController.unprovisionPin(outputPin);
			}
			this.gpioPinsByAddress.clear();
		} catch (Error e) {
			logger.error("Failed to disconnect {}", this.id, e);
		}

		super.disconnect();
	}

	@Override
	public void send(String address, Object value) {
		if (this.simulated) {
			logger.warn("{}: Running SIMULATED, NOT CONNECTING!", this.id);
			return;
		}

		boolean high = (boolean) value;
		if (this.inverted)
			high = !high;

		GpioPinDigitalOutput outputPin = this.gpioPinsByAddress.get(address);
		if (outputPin == null)
			throw new IllegalArgumentException("Output pin with address " + address + " does not exist!");

		PinState newState = high ? PinState.HIGH : PinState.LOW;
		if (this.verbose)
			logger.info("Setting pin {} to new state {}", outputPin, newState);
		outputPin.setState(newState);
	}

	@Override
	public Set<String> getAddresses() {
		return this.pinsByAddress.keySet();
	}
}
