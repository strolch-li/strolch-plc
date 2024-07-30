package li.strolch.plc.core.hw;

import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.PlcAddress;

/**
 * The interface which can be notified by the {@link PlcHandler} when an event is detected from the hardware.
 */
public interface PlcListener {

	/**
	 * Notifies the listener of the new value at the given address
	 *
	 * @param address the address at which the event was detected
	 * @param value   the new value at the address
	 */
	void handleNotification(PlcAddress address, Object value);
}
