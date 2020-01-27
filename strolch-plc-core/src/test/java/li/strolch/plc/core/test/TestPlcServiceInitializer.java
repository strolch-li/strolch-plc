package li.strolch.plc.core.test;

import java.util.ArrayList;
import java.util.List;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;
import li.strolch.plc.core.PlcServiceInitializer;

public class TestPlcServiceInitializer extends PlcServiceInitializer {

	public TestPlcServiceInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	protected List<PlcService> getPlcServices(PlcHandler plcHandler) {
		ArrayList<PlcService> plcServices = new ArrayList<>();
		plcServices.add(new StartupPlcService(getContainer(), plcHandler));
		plcServices.add(new TogglePlcService(getContainer(), plcHandler));
		return plcServices;
	}
}
