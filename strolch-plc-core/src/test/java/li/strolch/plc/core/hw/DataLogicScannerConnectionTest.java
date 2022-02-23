package li.strolch.plc.core.hw;

import java.util.HashMap;
import java.util.Map;

import li.strolch.model.StrolchValueType;
import li.strolch.plc.core.hw.connections.DataLogicScannerConnection;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;

public class DataLogicScannerConnectionTest {

	public static void main(String[] args) throws Exception {

		Map<String, Object> parameters = new HashMap<>();
		parameters.put("address", "192.168.1.245:51236");
		parameters.put("readTimeout", 60);

		Plc plc = new DefaultPlc();
		DataLogicScannerConnection scanner = new DataLogicScannerConnection(plc, "test");
		scanner.initialize(parameters);

		plc.addConnection(scanner);

		PlcAddress address = new PlcAddress(PlcAddressType.Notification, "resource", "action", "address",
				StrolchValueType.BOOLEAN, "-", false);
		plc.register(address, (a, value) -> System.out.print(a + ": " + value));

		plc.start();

		scanner.connect();
		scanner.send("test.trigger", true);

		Thread.sleep(30000L);

		scanner.send("test.trigger", false);
		scanner.disconnect();

		plc.stop();
	}
}
