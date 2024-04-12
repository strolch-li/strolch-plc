package li.strolch.plc.core.hw.i2c;

import com.pi4j.io.i2c.I2CDevice;
import li.strolch.plc.core.hw.Plc;

import java.io.IOException;

import static li.strolch.utils.helper.ByteHelper.*;
import static li.strolch.utils.helper.StringHelper.toHexString;

public class TCA9534OutputConnection extends Multi8BitI2cOutputConnection {

	private static final byte TCA9534_REG_ADDR_OUT_PORT = 0x01;
	private static final byte TCA9534_REG_ADDR_CFG = 0x03;

	public TCA9534OutputConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public String getName() {
		return "TCA9534";
	}

	@Override
	protected boolean setup() throws IOException {
		boolean ok = super.setup();

		if (ok)
			return true;

		handleBrokenConnection("Failed to configure " + getDescription(), null);

		return false;
	}

	protected boolean setup(byte address, int index, I2CDevice i2cDev) throws IOException {
		boolean ok = true;

		// first read configuration
		int config = i2cDev.read(TCA9534_REG_ADDR_CFG);
		if (config < 0)
			throw new IllegalStateException(
					"Failed to read configuration from address 0x" + toHexString(TCA9534_REG_ADDR_CFG));

		if (config != 0x00) {
			logger.warn("{} is not configured as OUTPUT, setting register 0x{} to 0x00", getDescription(address),
					toHexString(TCA9534_REG_ADDR_CFG));
			i2cDev.write(TCA9534_REG_ADDR_OUT_PORT, (byte) 0x00);
			i2cDev.write(TCA9534_REG_ADDR_CFG, (byte) 0x00);
		}

		if (this.resetOnConnect) {

			// default is all outputs off, i.e. 0
			this.states[index] = (byte) 0x00;
			try {
				i2cDev.write(TCA9534_REG_ADDR_OUT_PORT, this.states[index]);
				logger.info("Set initial value to {} for {}", asBinary((byte) 0x00), getDescription(address));
			} catch (Exception e) {
				ok = false;
				logger.error("Failed to set initial value to {} for {}", asBinary((byte) 0x00), getDescription(address),
						e);
			}
		} else {
			byte currentState = (byte) i2cDev.read(TCA9534_REG_ADDR_OUT_PORT);

			if (this.reversed)
				currentState = reverse(currentState);

			this.states[index] = currentState;
			logger.info("Initial value is {} for {}", asBinary(this.states[index]), getDescription(address));
		}

		return ok;
	}

	@Override
	protected void setPin(int device, int pin, I2CDevice outputDevice, boolean high) throws IOException {
		byte newState;
		if (high)
			newState = setBit(this.states[device], pin);
		else
			newState = clearBit(this.states[device], pin);

		byte writeState = this.reversed ? reverse(newState) : newState;

		if (this.verbose)
			logger.info("Setting {} to new state {}", getDescription((byte) outputDevice.getAddress()),
					asBinary(writeState));

		outputDevice.write(TCA9534_REG_ADDR_OUT_PORT, writeState);
		this.states[device] = newState;
	}
}