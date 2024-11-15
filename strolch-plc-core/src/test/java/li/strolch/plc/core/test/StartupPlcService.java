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

import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;

public class StartupPlcService extends PlcService {

	public static final String PLC = "PLC";
	public static final String STARTED = "Started";

	public StartupPlcService(PlcHandler plcHandler) {
		super(plcHandler);
	}

	@Override
	public void start(StrolchTransaction tx) {
		notify(PLC, STARTED, true);
		super.start(tx);
	}

	@Override
	public void stop() {
		notify(PLC, STARTED, false);
		super.stop();
	}
}
