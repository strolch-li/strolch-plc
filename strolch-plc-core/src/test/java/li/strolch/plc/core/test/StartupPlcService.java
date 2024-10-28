package li.strolch.plc.core.test;

import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;

public class StartupPlcService extends PlcService {

	public static final String PLC = "PLC";
	public static final String STARTED = "Started";

	public StartupPlcService(PlcHandler plcHandler) {
		super(plcHandler);
	}

	@Override
	public void start(StrolchTransaction tx) {
		notify(PLC, STARTED, true);
		super.start(tx);
	}

	@Override
	public void stop() {
		notify(PLC, STARTED, false);
		super.stop();
	}
}
