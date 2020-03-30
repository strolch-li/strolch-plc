package li.strolch.plc.core.hw.gpio;

import static java.util.stream.Collectors.joining;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;

import java.util.*;

import com.pi4j.io.gpio.*;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.connections.SimplePlcConnection;

public class RaspiBcmGpioOutputConnection extends SimplePlcConnection {

	private List<Integer> outputBcmAddresses;
	private Map<String, Pin> pinsByAddress;
	private Map<String, GpioPinDigitalOutput> gpioPinsByAddress;
	private boolean inverted;

	public RaspiBcmGpioOutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		@SuppressWarnings("unchecked")
		List<Integer> bcmOutputPins = (List<Integer>) parameters.get("bcmOutputPins");
		this.outputBcmAddresses = bcmOutputPins;

		this.pinsByAddress = new HashMap<>();
		for (Integer address : this.outputBcmAddresses) {
			Pin pin = RaspiBcmPin.getPinByAddress(address);
			if (pin == null)
				throw new IllegalArgumentException("RaspiBcmPin " + address + " does not exist!");
			String key = this.id + "." + address;
			this.pinsByAddress.put(key, pin);
			logger.info("Registered address " + key + " for RaspiBcmPin " + pin);
		}

		this.inverted = parameters.containsKey("inverted") && (boolean) parameters.get("inverted");
		logger.info(
				"Configured Raspi BCM GPIO Output for Pins " + this.outputBcmAddresses.stream().map(Object::toString)
						.collect(joining(", ")));
	}

	@Override
	public boolean connect() {
		try {
			GpioController gpioController = PlcGpioController.getInstance();

			this.gpioPinsByAddress = new HashMap<>();
			for (String address : this.pinsByAddress.keySet()) {
				Pin pin = this.pinsByAddress.get(address);
				GpioPinDigitalOutput outputPin = gpioController.provisionDigitalOutputPin(pin);
				this.gpioPinsByAddress.put(address, outputPin);
				logger.info("Provisioned output pin  " + outputPin + " for address " + address);
			}

			return super.connect();

		} catch (Throwable e) {
			handleBrokenConnection("Failed to connect to GpioController: " + getExceptionMessageWithCauses(e), e);
			return false;
		}
	}

	@Override
	public void disconnect() {
		try {
			GpioController gpioController = PlcGpioController.getInstance();
			for (GpioPinDigitalOutput outputPin : this.gpioPinsByAddress.values()) {
				gpioController.unprovisionPin(outputPin);
			}
			this.gpioPinsByAddress.clear();
		} catch (Error e) {
			logger.error("Failed to disconnect " + this.id, e);
		}

		super.disconnect();
	}

	@Override
	public void send(String address, Object value) {

		boolean high = (boolean) value;
		if (this.inverted)
			high = !high;

		GpioPinDigitalOutput outputPin = this.gpioPinsByAddress.get(address);
		if (outputPin == null)
			throw new IllegalArgumentException("Output pin with address " + address + " does not exist!");

		if (high)
			outputPin.setState(PinState.HIGH);
		else
			outputPin.setState(PinState.LOW);
	}

	@Override
	public Set<String> getAddresses() {
		return this.pinsByAddress.keySet();
	}
}
