/*
 * Copyright (c) 2024 Robert von Burg <eitch@eitchnet.ch>
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

public class PlcAddressValueResponse extends PlcAddressResponse {

	private Object value;

	public PlcAddressValueResponse(String plcId, PlcAddressKey plcAddressKey) {
		super(plcId, plcAddressKey);
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public Object getValue() {
		return this.value;
	}

	public boolean getValueAsBoolean() {
		return (Boolean) this.value;
	}

	public int getValueAsInt() {
		return ((Number) this.value).intValue();
	}

	public double getValueAsDouble() {
		return ((Number) this.value).doubleValue();
	}

	public String getValueAsString() {
		return ((String) this.value);
	}

	@Override
	public PlcAddressValueResponse state(PlcResponseState state, String stateMsg) {
		super.state(state, stateMsg);
		return this;
	}

	@Override
	public String toString() {
		return format(
				"PlcAddressValueResponse'{'plcId=''{0}'', sequenceId={1}, state={2}, stateMsg=''{3}'', value={4}'}'",
				plcId, sequenceId, state, stateMsg, value);
	}
}
