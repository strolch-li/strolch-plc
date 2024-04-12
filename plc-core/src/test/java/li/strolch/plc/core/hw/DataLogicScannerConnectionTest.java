package li.strolch.plc.core.hw;

import java.util.HashMap;
import java.util.Map;

import li.strolch.model.StrolchValueType;
import li.strolch.plc.core.hw.connections.DataLogicScannerConnection;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataLogicScannerConnectionTest {

	private static final Logger logger = LoggerFactory.getLogger(DataLogicScannerConnectionTest.class);

	public static void main(String[] args) throws Exception {

		Map<String, Object> parameters = new HashMap<>();
		parameters.put("address", "10.42.0.93:51236");
		parameters.put("readTimeout", 60);

		Plc plc = new DefaultPlc();
		DataLogicScannerConnection scanner = new DataLogicScannerConnection(plc, "test");
		scanner.initialize(parameters);

		plc.addConnection(scanner);

		PlcAddress address = new PlcAddress(PlcAddressType.Notification, "resource", "action", "address",
				StrolchValueType.BOOLEAN, "-", false, false);
		plc.register(address, (a, value) -> System.out.print(a + ": " + value));

		plc.start();
		logger.info("PLC Started.");

		logger.info("Connecting to scanner...");
		scanner.connect();
		logger.info("Connected to scanner {}", scanner.getId());
		logger.info("Sending trigger...");
		scanner.send("test.trigger", true);
		logger.info("Trigger sent.");

		Thread.sleep(30000L);

		logger.info("Sending trigger...");
		scanner.send("test.trigger", false);
		logger.info("Trigger sent.");

		logger.info("Disconnecting from scanner...");
		scanner.disconnect();

		logger.info("Stopping PLC...");
		plc.stop();
	}
}
