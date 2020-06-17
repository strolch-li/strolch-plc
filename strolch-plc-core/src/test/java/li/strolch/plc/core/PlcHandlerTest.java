package li.strolch.plc.core;

import static li.strolch.plc.model.PlcConstants.PARAM_VALUE;
import static li.strolch.plc.model.PlcConstants.TYPE_PLC_ADDRESS;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import li.strolch.model.Locator;
import li.strolch.model.Resource;
import li.strolch.model.log.LogMessage;
import li.strolch.model.parameter.BooleanParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.model.ConnectionState;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
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
	public static void beforeClass() {
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
	public void shouldStartPlcHandler() {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);
		assertEquals(PlcState.Started, plcHandler.getPlcState());

		PlcConnection loggerOutput = plcHandler.getPlc().getConnection("loggerOutput");
		assertEquals(ConnectionState.Connected, loggerOutput.getState());

		PlcConnection barcodeReader = plcHandler.getPlc().getConnection("barcodeReader");
		assertEquals(ConnectionState.Connected, barcodeReader.getState());

		String plcAddressId = plcHandler.getPlcAddressId("PLC", "Started");
		assertEquals("addrPlcStarted", plcAddressId);
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			Resource plcStartedAddr = tx.getResourceBy(TYPE_PLC_ADDRESS, plcAddressId, true);
			BooleanParameter valueP = plcStartedAddr.getParameter(PARAM_VALUE, true);
			assertEquals(true, valueP.getValue());
		}
	}

	@Test
	public void shouldNotifyPlcService() {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);
		AtomicReference<String> value = new AtomicReference<>("");
		plcHandler.register("BarcodeReader", "Barcode", (address, v) -> value.set((String) v));
		plcHandler.send("BarcodeReader", "ReadBarcode", "DoRead");
		assertNotEquals("", value.get());
	}

	@Test
	public void shouldSendVirtualBoolean() throws InterruptedException {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);
		String addressId = plcHandler.getPlcAddressId("PLC", "Running");
		AtomicBoolean value = new AtomicBoolean(getAddress(addressId).getBoolean(PARAM_VALUE));
		assertFalse(value.get());

		plcHandler.register("PLC", "Running", (address, v) -> {
			logger.error("Setting " + address + " to " + v);
			value.set((Boolean) v);
		});
		plcHandler.register("PLC", "NotRunning", (address, v) -> {
			logger.error("Setting " + address + " to " + v);
			value.set((Boolean) v);
		});

		plcHandler.send("PLC", "Running");
		assertTrue(value.get());
		assertTrue(getAddress(addressId).getBoolean(PARAM_VALUE));

		plcHandler.send("PLC", "NotRunning");
		assertFalse(value.get());
		assertFalse(getAddress(addressId).getBoolean(PARAM_VALUE));
	}

	private Resource getAddress(String addressId) {
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			tx.lock(Resource.locatorFor(TYPE_PLC_ADDRESS, addressId));
			return tx.getResourceBy(TYPE_PLC_ADDRESS, addressId, true);
		}
	}

	@Test
	public void shouldSendVirtualBooleanGlobal() {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);

		String addressId = plcHandler.getPlcAddressId("PLC", "Running");
		AtomicBoolean value = new AtomicBoolean(getAddress(addressId).getBoolean(PARAM_VALUE));
		assertFalse(value.get());

		plcHandler.setGlobalListener(new GlobalPlcListener() {
			@Override
			public void sendMsg(LogMessage message) {
				// ignore
			}

			@Override
			public void disableMsg(Locator locator) {
				// ignore
			}

			@Override
			public void handleNotification(PlcAddress address, Object v) {
				if (address.action.contains("Running"))
					value.set((Boolean) v);
			}
		});

		plcHandler.send("PLC", "Running");
		assertTrue(value.get());

		plcHandler.send("PLC", "NotRunning");
		assertFalse(value.get());
	}

	@Test
	public void shouldSendVirtualString() {

		PlcHandler plcHandler = runtimeMock.getComponent(PlcHandler.class);
		String addressId = plcHandler.getPlcAddressId("Server", "Connected");
		AtomicReference<String> value;
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			Resource address = tx.getResourceBy(TYPE_PLC_ADDRESS, addressId, true);
			value = new AtomicReference<>(address.getParameter(PARAM_VALUE, true).getValue());
		}
		assertEquals("Disconnected", value.get());

		plcHandler.register("Server", "Connected", (address, v) -> value.set((String) v));
		plcHandler.register("Server", "Disconnected", (address, v) -> value.set((String) v));

		plcHandler.send("Server", "Connected", "Connected");
		assertEquals("Connected", value.get());
		plcHandler.send("Server", "Connected", "Disconnected");
		assertEquals("Disconnected", value.get());
	}
}
