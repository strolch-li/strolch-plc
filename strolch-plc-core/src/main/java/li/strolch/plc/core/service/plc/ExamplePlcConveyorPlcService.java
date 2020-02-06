package li.strolch.plc.core.service.plc;

import java.util.concurrent.atomic.AtomicBoolean;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;
import li.strolch.plc.model.PlcAddress;

public class ExamplePlcConveyorPlcService extends PlcService {

	public static final String R_CONVEYOR_01 = "Conveyor01";
	public static final String R_CONVEYOR_02 = "Conveyor02";
	public static final String R_CONVEYOR_03 = "Conveyor03";
	public static final String R_CONVEYOR_04 = "Conveyor04";

	public static final String A_OCCUPIED = "Occupied";
	public static final String A_MOTOR_ON = "MotorOn";
	public static final String A_MOTOR_OFF = "MotorOff";
	public static final String A_BARCODE = "Barcode";
	public static final String A_READ_BARCODE = "ReadBarcode";

	private AtomicBoolean conveyor1Occupied;
	private AtomicBoolean conveyor2Occupied;
	private AtomicBoolean conveyor3Occupied;
	private AtomicBoolean conveyor4Occupied;

	private AtomicBoolean conveyor1On;
	private AtomicBoolean conveyor2On;
	private AtomicBoolean conveyor3On;
	private AtomicBoolean conveyor4On;

	private AtomicBoolean conveyor1WaitingForTransfer;
	private AtomicBoolean conveyor2WaitingForTransfer;
	private AtomicBoolean conveyor3WaitingForTransfer;

	public ExamplePlcConveyorPlcService(ComponentContainer container, PlcHandler plcHandler) {
		super(container, plcHandler);

		this.conveyor1WaitingForTransfer = new AtomicBoolean(false);
		this.conveyor2WaitingForTransfer = new AtomicBoolean(false);
		this.conveyor3WaitingForTransfer = new AtomicBoolean(false);
	}

	@Override
	public void handleNotification(PlcAddress address, Object value) {

		String resource = address.resource;
		String action = address.action;
		boolean state = (boolean) value;

		switch (resource) {

		case R_CONVEYOR_01:

			if (action.equals(A_OCCUPIED)) {
				conveyor1Occupied.set(state);
				handleTransfer(null, R_CONVEYOR_01, R_CONVEYOR_02, //
						null, conveyor1Occupied, conveyor2Occupied, //
						null, conveyor1On, conveyor2On, //
						null, conveyor1WaitingForTransfer);
			} else {
				logger.error("Unhandled action " + resource + "-" + action);
			}

			break;

		case R_CONVEYOR_02:

			if (action.equals(A_OCCUPIED)) {
				conveyor2Occupied.set(state);
				handleTransfer(R_CONVEYOR_01, R_CONVEYOR_02, R_CONVEYOR_03, //
						conveyor1Occupied, conveyor2Occupied, conveyor3Occupied, //
						conveyor1On, conveyor2On, conveyor3On, //
						conveyor1WaitingForTransfer, conveyor2WaitingForTransfer);
			} else {
				logger.error("Unhandled action " + resource + "-" + action);
			}

			break;

		case R_CONVEYOR_03:

			if (action.equals(A_OCCUPIED)) {
				conveyor3Occupied.set(state);
				handleTransfer(R_CONVEYOR_02, R_CONVEYOR_03, R_CONVEYOR_04, //
						conveyor2Occupied, conveyor3Occupied, conveyor4Occupied, //
						conveyor2On, conveyor3On, conveyor4On, //
						conveyor2WaitingForTransfer, conveyor3WaitingForTransfer);
			} else {
				logger.error("Unhandled action " + resource + "-" + action);
			}

			break;

		case R_CONVEYOR_04:

			if (action.equals(A_OCCUPIED)) {
				conveyor4Occupied.set(state);
				handleTransfer(R_CONVEYOR_03, R_CONVEYOR_04, null, //
						conveyor3Occupied, conveyor4Occupied, null, //
						conveyor3On, conveyor4On, null, //
						conveyor3WaitingForTransfer, null);
			} else {
				logger.error("Unhandled action " + resource + "-" + action);
			}

			break;
		}
	}

	private void handleTransfer(String previous, String current, String next, //
			AtomicBoolean previousOccupied, AtomicBoolean currentOccupied, AtomicBoolean nextOccupied, //
			AtomicBoolean previousOn, AtomicBoolean currentOn, AtomicBoolean nextOn, //
			AtomicBoolean previousWaitingForTransfer, AtomicBoolean currentWaitingForTransfer) {

		if (currentOccupied.get()) {

			// handle current conveyor is now occupied
			if (next == null) {
				if (currentOn.get()) {
					logger.info(current + " is now occupied without a next conveyor, stopping conveyor");
					send(current, A_MOTOR_OFF);
					currentOn.set(false);
				} else {
					logger.info(current + " is now occupied, conveyor is off and no next conveyor: transfer complete.");
				}

				return;
			}

			if (nextOccupied.get()) {
				logger.info(current + " is now occupied, next conveyor " + next + " is still occupied, so waiting...");
				if (currentWaitingForTransfer.get())
					logger.error("What the hell, current " + current + " is already waiting for a transfer!");
				currentWaitingForTransfer.set(true);
			} else {
				logger.info(
						current + " is now occupied, next conveyor " + next + " is not occupied, so transferring...");

				if (nextOn.get()) {
					logger.info("Next conveyor " + next + " is already running, waiting for transfer to complete...");
				} else {
					logger.info("Starting " + next + " and waiting for transfer to complete...");
					send(next, A_MOTOR_ON);
					nextOn.set(true);
				}

				if (currentOn.get()) {
					logger.info(current + " is already running, waiting for transfer to complete...");
				} else {
					logger.info("Starting " + current + " and waiting for transfer to complete...");
					send(current, A_MOTOR_ON);
					currentOn.set(true);
				}
			}

			return;
		}

		// current conveyor is not occupied anymore

		if (previous == null) {

			// no previous conveyor, so just stop current, if still on

			if (currentOn.get()) {
				logger.info(current + " is now unoccupied, stopping conveyor");
				send(current, A_MOTOR_OFF);
				currentOn.set(false);
			} else {
				logger.info(current + " is now unoccupied, conveyor is already off");
			}

			return;
		}

		// handle transfer of previous to current

		if (!previousOccupied.get()) {
			logger.info(previous + " is not occupied, so no transfer required.");

			if (currentOn.get()) {
				logger.info(current + " is now unoccupied and previous " + previous
						+ " is not occupied, so no transfer required: Stopping conveyor");
				send(current, A_MOTOR_OFF);
				currentOn.set(false);
			} else {
				logger.info(current + " is now unoccupied and previous " + previous
						+ " is not occupied, and conveyor not running. Nothing else to do");
			}

			return;
		}

		// previous is occupied, so transfer to current, but only if previous was waiting
		if (!previousWaitingForTransfer.get()) {
			logger.info(previous + " conveyor is not waiting for a transfer. Nothing else to do.");
		} else {

			logger.info(previous + " conveyor is waiting for a transfer, so starting transfer");

			if (currentOn.get()) {
				logger.info(current + " is already on, waiting for transfer...");
			} else {
				logger.info("Turning " + current + " on for transfer");
				send(current, A_MOTOR_ON);
				currentOn.set(true);
			}

			if (previousOn.get()) {
				logger.info(previous + " is already on, waiting for transfer...");
			} else {
				logger.info("Turning " + previous + " on for transfer");
				send(previous, A_MOTOR_ON);
				previousOn.set(true);
			}
		}
	}

	@Override
	public void register() {
		this.plcHandler.registerListener(R_CONVEYOR_01, A_OCCUPIED, this);
		this.plcHandler.registerListener(R_CONVEYOR_02, A_OCCUPIED, this);
		this.plcHandler.registerListener(R_CONVEYOR_03, A_OCCUPIED, this);
		this.plcHandler.registerListener(R_CONVEYOR_04, A_OCCUPIED, this);

		this.plcHandler.registerListener(R_CONVEYOR_03, A_BARCODE, this);
		super.register();
	}

	@Override
	public void unregister() {
		this.plcHandler.unregisterListener(R_CONVEYOR_01, A_OCCUPIED, this);
		this.plcHandler.unregisterListener(R_CONVEYOR_02, A_OCCUPIED, this);
		this.plcHandler.unregisterListener(R_CONVEYOR_03, A_OCCUPIED, this);
		this.plcHandler.unregisterListener(R_CONVEYOR_04, A_OCCUPIED, this);

		this.plcHandler.unregisterListener(R_CONVEYOR_03, A_BARCODE, this);
		super.unregister();
	}

	@Override
	public void start(StrolchTransaction tx) {

		this.conveyor1Occupied = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_01, A_OCCUPIED));
		this.conveyor2Occupied = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_02, A_OCCUPIED));
		this.conveyor3Occupied = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_03, A_OCCUPIED));
		this.conveyor4Occupied = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_04, A_OCCUPIED));

		this.conveyor1On = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_01, A_MOTOR_ON));
		this.conveyor2On = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_02, A_MOTOR_ON));
		this.conveyor3On = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_03, A_MOTOR_ON));
		this.conveyor4On = new AtomicBoolean(getAddressState(tx, R_CONVEYOR_04, A_MOTOR_ON));

		super.start(tx);
	}
}
