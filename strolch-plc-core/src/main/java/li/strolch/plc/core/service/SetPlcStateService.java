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

package li.strolch.plc.core.service;

import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcServiceInitializer;
import li.strolch.plc.model.PlcState;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static li.strolch.plc.model.PlcConstants.PARAM_STATE;

public class SetPlcStateService extends AbstractService<StringMapArgument, ServiceResult> {

	@Override
	protected ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	@Override
	public StringMapArgument getArgumentInstance() {
		return new StringMapArgument();
	}

	@Override
	protected ServiceResult internalDoService(StringMapArgument arg) throws Exception {

		PlcState newState = PlcState.valueOf(arg.map.get(PARAM_STATE));

		PlcHandler plcHandler = getComponent(PlcHandler.class);
		PlcServiceInitializer plcServiceInitializer = getComponent(PlcServiceInitializer.class);

		switch (newState) {
			case Stopped -> {
				if (plcHandler.getPlcState() == PlcState.Stopped)
					return ServiceResult.error("Already stopped");
				plcServiceInitializer.stop();
				plcHandler.stopPlc();
			}
			case Started -> {
				if (plcHandler.getPlcState() == PlcState.Started)
					return ServiceResult.error("Already started");
				plcHandler.startPlc();
				plcServiceInitializer.start();
			}
			case Configured -> {
				if (!plcHandler.reconfigurePlc())
					return ServiceResult.error(plcHandler.getPlcStateMsg());
			}
			default -> throw new IllegalArgumentException("Can not switch to state " + newState);
		}

		return ServiceResult.success();
	}
}
