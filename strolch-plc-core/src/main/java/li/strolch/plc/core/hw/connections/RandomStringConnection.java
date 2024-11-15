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
import li.strolch.utils.helper.StringHelper;

import java.security.SecureRandom;

public class RandomStringConnection extends SimplePlcConnection {

	public RandomStringConnection(Plc plc, String id) {
		super(plc, id);
	}

	@Override
	public void send(String address, Object value) {
		assertConnected();
		PlcConnection.logger.info("Sending {} => {}", address, value);
		byte[] data = new byte[8];
		new SecureRandom().nextBytes(data);
		String newValue = StringHelper.toHexString(data);
		PlcConnection.logger.info("Generated random value {}", newValue);
		this.plc.syncNotify(address, newValue);
	}
}
