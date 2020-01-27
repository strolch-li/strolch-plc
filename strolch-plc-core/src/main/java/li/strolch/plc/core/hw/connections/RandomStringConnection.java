package li.strolch.plc.core.hw.connections;

import java.security.SecureRandom;

import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.utils.helper.StringHelper;

public class RandomStringConnection extends SimplePlcConnection {

	public RandomStringConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void send(String address, Object value) {
		assertConnected();
		PlcConnection.logger.info("Sending " + address + " => " + value);
		byte[] data = new byte[8];
		new SecureRandom().nextBytes(data);
		String newValue = StringHelper.toHexString(data);
		PlcConnection.logger.info("Generated random value " + newValue);
		this.plc.notify(address, newValue);
	}
}
