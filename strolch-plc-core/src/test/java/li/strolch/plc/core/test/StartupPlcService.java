package li.strolch.plc.core.test;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;

public class StartupPlcService extends PlcService {

	public static final String PLC = "PLC";
	public static final String STARTED = "Started";
	public static final String STOPPED = "Stopped";

	public StartupPlcService(ComponentContainer container, PlcHandler plcHandler) {
		super(container, plcHandler);
	}

	@Override
	public void start(StrolchTransaction tx) {
		send(PLC, STARTED);
		super.start(tx);
	}

	@Override
	public void stop() {
		send(PLC, STOPPED);
		super.stop();
	}
}
