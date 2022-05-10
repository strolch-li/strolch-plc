package li.strolch.plc.core;

import java.util.ArrayList;
import java.util.List;

import li.strolch.agent.api.ComponentContainer;

public class NoPlcServiceInitializer extends PlcServiceInitializer {

	public NoPlcServiceInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	protected List<PlcService> getPlcServices(PlcHandler plcHandler) {
		return new ArrayList<>();
	}
}
