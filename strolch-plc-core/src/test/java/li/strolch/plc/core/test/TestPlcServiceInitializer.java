package li.strolch.plc.core.test;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;
import li.strolch.plc.core.PlcServiceInitializer;

import java.util.ArrayList;
import java.util.List;

public class TestPlcServiceInitializer extends PlcServiceInitializer {

	public TestPlcServiceInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	protected List<PlcService> getPlcServices(PlcHandler plcHandler) {
		ArrayList<PlcService> plcServices = new ArrayList<>();
		plcServices.add(new StartupPlcService(plcHandler));
		plcServices.add(new TogglePlcService(plcHandler));
		return plcServices;
	}
}
