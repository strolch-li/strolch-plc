package li.strolch.plc.core.hw.gpio;

import static java.util.stream.Collectors.joining;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;

import java.util.*;

import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import com.pi4j.io.gpio.*;

public class RaspiBcmGpioOutputConnection extends PlcConnection {

	private List<Integer> outputBcmAddresses;
	private Map<String, GpioPinDigitalOutput> pinsByAddress;
	private boolean inverted;

	public RaspiBcmGpioOutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {
		@SuppressWarnings("unchecked")
		List<Integer> bcmOutputPins = (List<Integer>) parameters.get("bcmOutputPins");
		this.outputBcmAddresses = bcmOutputPins;
		this.inverted = parameters.containsKey("inverted") && (boolean) parameters.get("inverted");
		logger.info(
				"Configured Raspi BCM GPIO Output for Pins " + this.outputBcmAddresses.stream().map(Object::toString)
						.collect(joining(", ")));
	}

	@Override
	public void connect() {
		try {
			GpioController gpioController = PlcGpioController.getInstance();

			this.pinsByAddress = new HashMap<>();
			for (Integer address : this.outputBcmAddresses) {
				Pin pin = RaspiBcmPin.getPinByAddress(address);
				if (pin == null)
					throw new IllegalArgumentException("RaspiBcmPin " + address + " does not exist!");
				GpioPinDigitalOutput outputPin = gpioController.provisionDigitalOutputPin(pin);
				String key = this.id + "." + address;
				this.pinsByAddress.put(key, outputPin);
				logger.info("Registered address " + key + " for RaspiBcmPin " + outputPin);
			}

			logger.info(this.id + ": Is now connected.");
			this.connectionState = ConnectionState.Connected;
			this.connectionStateMsg = "-";
			this.plc.notifyConnectionStateChanged(this);

		} catch (Error e) {
			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg = "Failed to connect to GpioController: " + getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
		}
	}

	@Override
	public void disconnect() {
		try {
			GpioController gpioController = PlcGpioController.getInstance();
			for (GpioPinDigitalOutput outputPin : this.pinsByAddress.values()) {
				gpioController.unprovisionPin(outputPin);
			}
			this.pinsByAddress.clear();
		} catch (Error e) {
			logger.error("Failed to disconnect " + this.id, e);
		}

		this.connectionState = ConnectionState.Disconnected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	@Override
	public void send(String address, Object value) {

		boolean high = (boolean) value;
		if (this.inverted)
			high = !high;

		GpioPinDigitalOutput outputPin = this.pinsByAddress.get(address);
		if (outputPin == null)
			throw new IllegalArgumentException("Output pin with address " + address + " does not exist!");

		if (high)
			outputPin.setState(PinState.HIGH);
		else
			outputPin.setState(PinState.LOW);
	}

	@Override
	public Set<String> getAddresses() {
		Set<String> addresses = new HashSet<>();
		for (Integer address : this.outputBcmAddresses) {
			addresses.add(this.id + "." + address);
		}
		return addresses;
	}
}
