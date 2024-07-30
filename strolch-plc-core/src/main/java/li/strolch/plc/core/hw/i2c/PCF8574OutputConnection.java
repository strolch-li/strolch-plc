package li.strolch.plc.core.hw.i2c;

import static li.strolch.utils.helper.ByteHelper.*;

import java.io.IOException;

import com.pi4j.io.i2c.I2CDevice;
import li.strolch.plc.core.hw.Plc;

public class PCF8574OutputConnection extends Multi8BitI2cOutputConnection {

	public PCF8574OutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public String getName() {
		return "PCF8574";
	}

	@Override
	protected boolean setup() throws IOException {
		boolean ok = super.setup();

		if (ok)
			return true;

		handleBrokenConnection("Failed to set initial values to " + asBinary((byte) 0xff) + " for " + getDescription(),
				null);

		return false;
	}

	@Override
	protected boolean setup(byte address, int index, I2CDevice i2cDev) throws IOException {
		boolean ok = true;

		if (this.resetOnConnect) {
			// default is all outputs off, i.e. 1
			this.states[index] = (byte) 0xff;
			try {
				i2cDev.write(this.states[index]);
				logger.info("{}: set initial value to {} for {}", this.id, asBinary((byte) 0xff),
						getDescription(address));
			} catch (Exception e) {
				ok = false;
				logger.error("{}: Failed to set initial value to {} for {}", this.id, asBinary((byte) 0xff),
						getDescription(address), e);
			}
		} else {
			this.states[index] = (byte) i2cDev.read();
			logger.info("{}: Initial value is {} for {}", this.id, asBinary(this.states[index]),
					getDescription(address));
		}

		return ok;
	}

	@Override
	protected void setPin(int device, int pin, I2CDevice outputDevice, boolean high) throws IOException {
		byte newState;
		if (high)
			newState = clearBit(this.states[device], pin);
		else
			newState = setBit(this.states[device], pin);

		logger.info("Setting {}.{}.{} = {} ({})", this.id, device, pin, high ? 0 : 1, asBinary(newState));

		outputDevice.write(newState);
		this.states[device] = newState;
	}
}