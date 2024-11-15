/*
 * Copyright (c) 2013-2024 Robert von Burg <eitch@eitchnet.ch>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
