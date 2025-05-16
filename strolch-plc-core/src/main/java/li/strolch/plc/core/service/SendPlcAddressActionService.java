/*
 * Copyright (c) 2013-2025 Robert von Burg <eitch@eitchnet.ch>
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

import com.google.gson.JsonObject;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.service.JsonServiceArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static li.strolch.plc.model.PlcConstants.*;

public class SendPlcAddressActionService extends AbstractService<JsonServiceArgument, ServiceResult> {
	@Override
	protected ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	@Override
	public JsonServiceArgument getArgumentInstance() {
		return new JsonServiceArgument();
	}

	@Override
	protected ServiceResult internalDoService(JsonServiceArgument arg) throws Exception {

		JsonObject jsonObject = arg.jsonElement.getAsJsonObject();
		PlcAddressType addressType = PlcAddressType.valueOf(jsonObject.get(PARAM_TYPE).getAsString());
		String resource = jsonObject.get(PARAM_RESOURCE).getAsString();
		String action = jsonObject.get(PARAM_ACTION).getAsString();

		PlcHandler plcHandler = getComponent(PlcHandler.class);
		PlcAddress plcAddress = plcHandler.getPlcAddress(resource, action);

		if (addressType == PlcAddressType.Telegram) {
			if (jsonObject.has(PARAM_VALUE)) {
				String valueS = jsonObject.get(PARAM_VALUE).getAsString();
				Object value = plcAddress.valueType.parseValue(valueS);
				logger.info("PLC Send {}-{} with {}", resource, action, valueS);
				plcHandler.send(resource, action, value);
			} else {
				logger.info("PLC Send {}-{} with default value {}", resource, action, plcAddress.defaultValue);
				plcHandler.send(resource, action);
			}
		} else if (addressType == PlcAddressType.Notification) {
			if (!jsonObject.has(PARAM_VALUE))
				throw new IllegalArgumentException("For notification a value must be set!");

			String valueS = jsonObject.get(PARAM_VALUE).getAsString();
			Object value = plcAddress.valueType.parseValue(valueS);

			logger.info("PLC Notification {}-{} with {}", resource, action, valueS);
			plcHandler.notify(resource, action, value);

		} else {
			throw new UnsupportedOperationException("Unhandled address type " + addressType);
		}

		return ServiceResult.success();
	}
}
