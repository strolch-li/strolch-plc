package li.strolch.plc.core.hw;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import li.strolch.plc.core.ConnectionState;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlcTest {

	private static final Logger logger = LoggerFactory.getLogger(PlcTest.class);

	private static Plc plc;

	// conveyor: on/off and occupied
	private static TestPlcConnection conveyorCon;
	private static AtomicBoolean stateOnOff;
	private static PlcAddress positionOn;
	private static PlcAddress positionOff;
	private static PlcAddress positionOccupied;

	@BeforeClass
	public static void beforeClass() {

		// plc
		plc = new DefaultPlc();

		// conveyor connection with keys
		stateOnOff = new AtomicBoolean();
		conveyorCon = new TestPlcConnection(plc, "Connection.Conveyor01",
				new HashSet<>(asList("Conveyor.Occupied", "Conveyor.OnOff")), e -> stateOnOff.set((Boolean) e));
		conveyorCon.initialize(Collections.emptyMap());
		plc.addConnection(conveyorCon);
		positionOn = new PlcAddress(PlcAddressType.Telegram, false, "Conveyor", "On", "Conveyor.OnOff",
				PlcValueType.Boolean, true, false);
		positionOff = new PlcAddress(PlcAddressType.Telegram, false, "Conveyor", "Off", "Conveyor.OnOff",
				PlcValueType.Boolean, false, false);
		positionOccupied = new PlcAddress(PlcAddressType.Notification, false, "Conveyor", "Occupied",
				"Conveyor.Occupied", PlcValueType.Boolean, null, false);
		plc.registerNotificationMapping(positionOccupied);
	}

	@Test
	public void shouldTestConveyorOnOff() {
		assertFalse(stateOnOff.get());
		plc.send(positionOn);
		assertTrue(stateOnOff.get());
		plc.send(positionOff);
		assertFalse(stateOnOff.get());
	}

	@Test
	public void shouldTestConveyorOccupied() {
		AtomicBoolean state = new AtomicBoolean(false);
		plc.registerListener(positionOccupied, (key, value) -> state.set((Boolean) value));
		conveyorCon.notify("Conveyor.Occupied", true);
		assertTrue(state.get());
		conveyorCon.notify("Conveyor.Occupied", false);
		assertFalse(state.get());
	}

	static class TestPlcConnection extends PlcConnection {

		private final Set<String> addresses;
		private final Consumer<Object> sendHandler;

		public TestPlcConnection(Plc plc, String id, Set<String> addresses, Consumer<Object> sendHandler) {
			super(plc, id);
			this.addresses = addresses;
			this.sendHandler = sendHandler;
		}

		@Override
		public void initialize(Map<String, Object> parameters) {
			// no-op
		}

		@Override
		public void connect() {
			this.connectionState = ConnectionState.Connected;
		}

		@Override
		public void disconnect() {
			//
		}

		@Override
		public Set<String> getAddresses() {
			return this.addresses;
		}

		@Override
		public void send(String address, Object value) {
			this.sendHandler.accept(value);
		}

		public void notify(String address, Object value) {
			this.plc.notify(address, value);
		}
	}
}
