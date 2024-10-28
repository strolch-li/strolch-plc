package li.strolch.plc.core;

import li.strolch.agent.api.ComponentContainer;

import java.util.ArrayList;
import java.util.List;

public class NoPlcServiceInitializer extends PlcServiceInitializer {

	public NoPlcServiceInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	protected List<PlcService> getPlcServices(PlcHandler plcHandler) {
		return new ArrayList<>();
	}
}
