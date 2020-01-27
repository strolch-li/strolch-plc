package li.strolch.plc.core.hw.i2c;

import static li.strolch.utils.helper.ByteHelper.clearBit;
import static li.strolch.utils.helper.ByteHelper.setBit;
import static li.strolch.utils.helper.ExceptionHelper.getExceptionMessageWithCauses;
import static li.strolch.utils.helper.StringHelper.toHexString;

import java.util.*;

import li.strolch.plc.core.ConnectionState;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PCF8574OutputConnection extends PlcConnection {

	private static final Logger logger = LoggerFactory.getLogger(PCF8574OutputConnection.class);

	private int i2cBusNr;
	private byte state;
	private byte address;
	private I2CDevice outputDev;

	private Map<String, Integer> positionsByAddress;

	public PCF8574OutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) {

		if (!parameters.containsKey("i2cBus"))
			throw new IllegalArgumentException("Missing param i2cBus");
		if (!parameters.containsKey("address"))
			throw new IllegalArgumentException("Missing param address");

		this.i2cBusNr = (int) parameters.get("i2cBus");
		String addressS = (String) parameters.get("address");
		this.address = Integer.decode(addressS).byteValue();

		Map<String, Integer> positionsByAddress = new HashMap<>();
		for (int i = 0; i < 8; i++)
			positionsByAddress.put(this.id + "." + i, i);
		this.positionsByAddress = Collections.unmodifiableMap(positionsByAddress);

		logger.info("Configured PCF8574 Output on I2C address " + addressS);
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
			I2CBus i2cBus = I2CFactory.getInstance(i2cBusNr);
			this.outputDev = i2cBus.getDevice(address);

			// default is all outputs off, i.e. 1
			this.state = (byte) 0xff;
			this.outputDev.write(this.state);

			logger.info("Connected to I2C Device at address 0x" + toHexString(this.address) + " on I2C Bus "
					+ this.i2cBusNr);

			logger.info(this.id + ": Is now connected.");
			this.connectionState = ConnectionState.Connected;
			this.connectionStateMsg = "-";
			this.plc.notifyConnectionStateChanged(this);

		} catch (Exception e) {
			logger.error("Failed to connect to I2C Bus " + this.i2cBusNr + " and address " + toHexString(this.address),
					e);

			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg =
					"Failed to connect to I2C Bus " + this.i2cBusNr + " and address " + toHexString(this.address) + ": "
							+ getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);
		}
	}

	@Override
	public void disconnect() {
		this.outputDev = null;

		this.connectionState = ConnectionState.Disconnected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	@Override
	public Set<String> getAddresses() {
		return new TreeSet<>(this.positionsByAddress.keySet());
	}

	@Override
	public void send(String address, Object value) {
		assertConnected();

		Integer pos = this.positionsByAddress.get(address);
		if (pos == null)
			throw new IllegalStateException("Address is illegal " + address);

		boolean high = (boolean) value;

		try {
			byte newState;
			if (high)
				newState = clearBit(this.state, pos);
			else
				newState = setBit(this.state, pos);

			this.outputDev.write(newState);
			this.state = newState;
		} catch (Exception e) {
			this.connectionState = ConnectionState.Failed;
			this.connectionStateMsg =
					"Failed to write to I2C address: " + address + ": " + getExceptionMessageWithCauses(e);
			this.plc.notifyConnectionStateChanged(this);

			throw new IllegalStateException("Failed to write to I2C address " + address, e);
		}
	}

	@Override
	public String toString() {
		return this.id;
	}
}
