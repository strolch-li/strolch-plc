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

package li.strolch.plc.core.hw.connections;

import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.plc.model.ConnectionState;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public abstract class SimplePlcConnection extends PlcConnection {

	protected boolean simulated;

	public SimplePlcConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void initialize(Map<String, Object> parameters) throws Exception {
		logger.info("Configured {} {}", getClass().getSimpleName(), this.id);
	}

	@Override
	public boolean connect() {
		logger.info("{}: Is now connected.", this.id);
		if (this.simulated)
			logger.info("Running SIMULATED");
		this.connectionState = ConnectionState.Connected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
		return true;
	}

	@Override
	public void disconnect() {
		logger.info("{}: Is now disconnected.", this.id);
		this.connectionState = ConnectionState.Disconnected;
		this.connectionStateMsg = "-";
		this.plc.notifyConnectionStateChanged(this);
	}

	@Override
	public Set<String> getAddresses() {
		TreeSet<String> addresses = new TreeSet<>();
		addresses.add(this.id);
		return Collections.unmodifiableSet(addresses);
	}
}
