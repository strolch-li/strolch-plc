package li.strolch.plc.core.hw.i2c;

import com.pi4j.io.i2c.I2CDevice;
import li.strolch.utils.communication.PacketObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static li.strolch.utils.helper.StringHelper.toHexString;
import static li.strolch.utils.helper.StringHelper.toPrettyHexString;

public class LoggingI2cDevice {

	private static final Logger logger = LoggerFactory.getLogger(LoggingI2cDevice.class);

	private final I2CDevice i2cDevice;
	private final String i2cAddressS;

	private long ioWait;
	private int ioWaitNanos;
	private long lastWriteNanos;

	private PacketObserver packetObserver;

	public LoggingI2cDevice(I2CDevice i2cDevice, PacketObserver packetObserver) {
		this.i2cDevice = i2cDevice;
		this.packetObserver = packetObserver;
		this.i2cAddressS = toHexString((byte) this.i2cDevice.getAddress());
	}

	public I2CDevice getI2cDevice() {
		return this.i2cDevice;
	}

	public void setPacketObserver(PacketObserver packetObserver) {
		this.packetObserver = packetObserver;
	}

	public int getAddress() {
		return this.i2cDevice.getAddress();
	}

	public void write(boolean log, byte data) throws IOException, InterruptedException {
		sleepIfNecessary();

		if (log)
			logger.info("{}:  Writing: {}", this.i2cAddressS, toHexString(data));

		this.i2cDevice.write(data);

		if (this.packetObserver != null)
			this.packetObserver.notifySent(new byte[]{data});
		this.lastWriteNanos = System.nanoTime();
	}

	public void write(boolean log, byte[] buffer) throws IOException, InterruptedException {
		sleepIfNecessary();

		if (log)
			logger.info("{}:  Writing: {}", this.i2cAddressS, toPrettyHexString(buffer));

		this.i2cDevice.write(buffer);

		if (this.packetObserver != null)
			this.packetObserver.notifySent(buffer);
		this.lastWriteNanos = System.nanoTime();
	}

	public void write(boolean log, int address, byte b) throws IOException, InterruptedException {
		write(log, new byte[]{(byte) address, b});
	}

	public void write(boolean log, int address, byte[] buffer) throws IOException, InterruptedException {
		byte[] data = new byte[buffer.length + 1];
		data[0] = (byte) address;
		System.arraycopy(buffer, 0, data, 1, buffer.length);
		write(log, data);
	}

	public void writeRead(boolean log, byte[] writeBuffer, byte[] readBuffer) throws IOException, InterruptedException {
		sleepIfNecessary();

		if (log)
			logger.info("{}:  Writing: {}", this.i2cAddressS, toPrettyHexString(writeBuffer));

		int read = this.i2cDevice.read(writeBuffer, 0, writeBuffer.length, readBuffer, 0, readBuffer.length);
		if (read != readBuffer.length)
			throw new IllegalStateException("Expected to read " + readBuffer.length + " bytes, but read " + read);

		if (this.packetObserver != null) {
			this.packetObserver.notifySent(writeBuffer);
			this.packetObserver.notifyReceived(readBuffer);
		}
		this.lastWriteNanos = System.nanoTime();

		if (log)
			logger.info("{}:  Read: {}", this.i2cAddressS, toPrettyHexString(readBuffer));
	}

	public int read(boolean log) throws IOException {
		int read = this.i2cDevice.read();

		if (log)
			logger.info("{}:  Read: {}", this.i2cAddressS, toHexString((byte) read));
		if (this.packetObserver != null)
			this.packetObserver.notifyReceived(new byte[]{(byte) read});

		return read;
	}

	public int read(boolean log, byte address) throws IOException {
		if (log)
			logger.info("{}:  Writing: {}", this.i2cAddressS, toHexString(address));

		int read = this.i2cDevice.read(address);

		if (log)
			logger.info("{}:  Read: {}", this.i2cAddressS, toHexString((byte) read));
		if (this.packetObserver != null) {
			this.packetObserver.notifySent(new byte[]{address});
			this.packetObserver.notifyReceived(new byte[]{(byte) read});
		}

		return read;
	}

	public void read(boolean log, byte[] buffer) throws IOException {
		if (log)
			logger.info("{}:  Reading: {} bytes from last set address...", this.i2cAddressS, buffer.length);

		int read = this.i2cDevice.read(buffer, 0, buffer.length);
		if (read != buffer.length)
			throw new IllegalStateException("Expected to read " + buffer.length + " bytes, but read " + read);

		if (log)
			logger.info("{}:  Read: {}", this.i2cAddressS, toPrettyHexString(buffer));
		if (this.packetObserver != null)
			this.packetObserver.notifyReceived(new byte[]{(byte) read});
	}

	public void read(boolean log, byte address, byte[] buffer) throws IOException {
		if (log)
			logger.info("{}:  Reading: {} bytes from address {}", this.i2cAddressS, buffer.length,
					toHexString(address));

		int read = this.i2cDevice.read(address, buffer, 0, buffer.length);
		if (read != buffer.length)
			throw new IllegalStateException("Expected to read " + buffer.length + " bytes, but read " + read);

		if (log)
			logger.info("{}:  Read: {}", this.i2cAddressS, toPrettyHexString(buffer));
		if (this.packetObserver != null) {
			this.packetObserver.notifySent(new byte[]{address});
			this.packetObserver.notifyReceived(buffer);
		}
	}

	private void sleepIfNecessary() throws InterruptedException {
		if (this.ioWait == 0L) {
			if (this.ioWaitNanos == 0L)
				return;

			long nextWriteNanos = this.lastWriteNanos + this.ioWaitNanos;
			long now = System.nanoTime();
			if (nextWriteNanos > now) {
				Thread.sleep(0L, (int) (nextWriteNanos - now));
			}
			return;
		}

		long nextWrite = (this.lastWriteNanos / 1000000) + this.ioWait;
		long now = System.currentTimeMillis();
		if (nextWrite > now) {
			Thread.sleep(nextWrite - now);
		}
	}

	public void setIoWait(long ioWait, int ioWaitNanos) {
		this.ioWait = ioWait;
		this.ioWaitNanos = ioWaitNanos;

		logger.info("Using {} ms and {} ns for write sleep", ioWait, ioWaitNanos);
	}
}
