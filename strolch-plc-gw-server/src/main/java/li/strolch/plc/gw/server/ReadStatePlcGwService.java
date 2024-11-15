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

package li.strolch.plc.gw.server;

import li.strolch.model.Resource;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressValueResponse;

import static li.strolch.plc.model.PlcConstants.PARAM_PLC_SUPPORTS_READ_STATE;

public abstract class ReadStatePlcGwService extends PlcGwService implements PlcConnectionStateListener {

	protected boolean plcSupportsReadState;

	public ReadStatePlcGwService(String plcId, PlcGwServerHandler plcHandler) {
		super(plcId, plcHandler);
	}

	protected void handleGetState(PlcAddressValueResponse response) {
		PlcAddressKey addressKey = response.getPlcAddressKey();
		if (response.isFailed()) {
			logger.error("Failed to read value for address {}: {}", addressKey, response.getStateMsg());
		} else {
			storeAddressState(addressKey, response.getValue());
		}
	}

	@Override
	public void register() {
		this.plcSupportsReadState = runReadOnlyTx((ctx, tx) -> {
			Resource configuration = tx.getConfiguration();
			return !configuration.hasParameter(PARAM_PLC_SUPPORTS_READ_STATE) || configuration.getBoolean(
					PARAM_PLC_SUPPORTS_READ_STATE);
		});
		super.register();
	}

	protected void readState(String resource, String action) {
		if (!this.plcSupportsReadState)
			logger.warn("Not reading state for resource {}: {} as PLC does not support this feature!", resource,
					action);
		else
			this.plcHandler.asyncGetAddressState(keyFor(resource, action), this.plcId, this::handleGetState);
	}

	protected abstract void storeAddressState(PlcAddressKey addressKey, Object value);
}
