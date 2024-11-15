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

import li.strolch.model.Tags;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.model.ConnectionState;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

public class SetPlcConnectionStateService extends AbstractService<StringMapArgument, ServiceResult> {

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

		String id = arg.map.get(Tags.Json.ID);
		String stateS = arg.map.get(Tags.Json.STATE);
		DBC.PRE.assertNotEmpty("id must be set!", id);
		DBC.PRE.assertNotEmpty("state must be set!", stateS);

		ConnectionState state = ConnectionState.valueOf(stateS);
		if (state != ConnectionState.Connected && state != ConnectionState.Disconnected)
			throw new IllegalArgumentException(
					"Only " + ConnectionState.Connected + " and " + ConnectionState.Disconnected + " states allowed!");

		PlcHandler plcHandler = getComponent(PlcHandler.class);
		PlcConnection plcConnection = plcHandler.getPlc().getConnection(id);

		if (state == ConnectionState.Connected)
			plcConnection.connect();
		else
			plcConnection.disconnect();

		return ServiceResult.success();
	}
}
