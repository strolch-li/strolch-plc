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

package li.strolch.plc.model;

import static java.text.MessageFormat.format;

public class PlcAddressResponse extends PlcResponse {

	private final PlcAddressKey plcAddressKey;

	public PlcAddressResponse(String plcId, PlcAddressKey plcAddressKey) {
		super(plcId);
		this.plcAddressKey = plcAddressKey;
	}

	@Override
	public PlcAddressResponse state(PlcResponseState state, String stateMsg) {
		super.state(state, stateMsg);
		return this;
	}

	public PlcAddressKey getPlcAddressKey() {
		return this.plcAddressKey;
	}

	@Override
	public String toString() {
		return format("PlcAddressResponse'{'plcId=''{0}'', plcAddressKey={1}, sequenceId={2}, state={3}'}'", plcId,
				plcAddressKey, sequenceId, state);
	}
}
