package li.strolch.plc.core;

import static li.strolch.plc.core.PlcConstants.PARAM_VALUE;
import static li.strolch.plc.core.PlcConstants.TYPE_PLC_ADDRESS;

import java.util.concurrent.atomic.AtomicReference;

import li.strolch.model.Resource;
import li.strolch.model.parameter.BooleanParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlcHandlerTest {

	private static final Logger logger = LoggerFactory.getLogger(PlcHandlerTest.class);
	private static final String SRC_RUNTIME = "src/test/resources/test-runtime";
	private static final String TARGET_PATH = "target/" + PlcHandlerTest.class.getSimpleName();
	private static RuntimeMock runtimeMock;
	private static Certificate cert;

	@BeforeClass
	public static void beforeClass() throws Exception {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SRC_RUNTIME);
		runtimeMock.startContainer();
		cert = runtimeMock.loginAdmin();
	}

	@AfterClass
	public static void afterClass() throws InterruptedException {
		if (cert != null)
			runtimeMock.logout(cert);
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();

		// wait for PLC's async updates to complete
		Thread.sleep(100L);
	}

	@Test
	public void shouldStartPlcHandler() throws InterruptedException {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);
		Assert.assertEquals(PlcState.Started, plcHandler.getPlcState());

		PlcConnection loggerOutput = plcHandler.getPlc().getConnection("loggerOutput");
		Assert.assertEquals(ConnectionState.Connected, loggerOutput.getState());

		PlcConnection barcodeReader = plcHandler.getPlc().getConnection("barcodeReader");
		Assert.assertEquals(ConnectionState.Connected, barcodeReader.getState());

		String plcAddressId = plcHandler.getPlcAddressId("PLC", "Started");
		Assert.assertEquals("addrPlcStarted", plcAddressId);
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			Resource plcStartedAddr = tx.getResourceBy(TYPE_PLC_ADDRESS, plcAddressId, true);
			BooleanParameter valueP = plcStartedAddr.getParameter(PARAM_VALUE, true);
			Assert.assertEquals(true, valueP.getValue());
		}
	}

	@Test
	public void shouldNotifyPlcService() throws InterruptedException {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);
		AtomicReference<String> value = new AtomicReference<>("");
		plcHandler.registerListener("BarcodeReader", "Barcode", (address, v) -> value.set((String) v));
		plcHandler.send("BarcodeReader", "ReadBarcode", "DoRead");
		Assert.assertNotEquals("", value.get());
	}
}
