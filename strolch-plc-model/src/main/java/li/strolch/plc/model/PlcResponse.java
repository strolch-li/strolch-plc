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

import li.strolch.utils.CheckedRunnable;

public class PlcResponse {

	private static long lastSequenceId = System.currentTimeMillis();

	protected final String plcId;
	protected final long sequenceId;
	protected PlcResponseState state;
	protected String stateMsg;

	private CheckedRunnable listener;

	public PlcResponse(String plcId) {
		this.plcId = plcId;
		this.sequenceId = lastSequenceId++;
		this.state = PlcResponseState.Pending;
		this.stateMsg = "";
	}

	public String getPlcId() {
		return this.plcId;
	}

	public long getSequenceId() {
		return this.sequenceId;
	}

	public PlcResponseState getState() {
		return this.state;
	}

	public void setState(PlcResponseState state) {
		this.state = state;
	}

	public PlcResponse state(PlcResponseState state, String stateMsg) {
		this.state = state;
		this.stateMsg = stateMsg;
		return this;
	}

	public boolean isDone() {
		return this.state == PlcResponseState.Done;
	}

	public boolean isSent() {
		return this.state == PlcResponseState.Sent;
	}

	public boolean isFailed() {
		return this.state == PlcResponseState.Failed;
	}

	public String getStateMsg() {
		return this.stateMsg;
	}

	public void setStateMsg(String stateMsg) {
		this.stateMsg = stateMsg;
	}

	public CheckedRunnable getListener() {
		return this.listener;
	}

	public void setListener(CheckedRunnable listener) {
		this.listener = listener;
	}
}
