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
import li.strolch.privilege.model.PrivilegeContext;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TogglePlcService extends PlcService {

	public static final String TOGGLER = "Toggler";
	public static final String ON = "On";
	public static final String OFF = "Off";

	private boolean on;
	private ScheduledFuture<?> toggler;

	public TogglePlcService(PlcHandler plcHandler) {
		super(plcHandler);
	}

	@Override
	public void start(StrolchTransaction tx) {
		this.on = getAddressState(tx, TOGGLER, ON);
		this.toggler = this.scheduleAtFixedRate(this::toggle, 10, 10, TimeUnit.SECONDS);
	}

	@Override
	public void stop() {
		if (this.toggler != null)
			this.toggler.cancel(true);
		this.toggler = null;
	}

	private void toggle(PrivilegeContext ctx) {
		if (this.on) {
			logger.info("Toggling Toggle to off!");
			send(TOGGLER, OFF);
			this.on = false;
		} else {
			logger.info("Toggling Toggle to on!");
			send(TOGGLER, ON);
			this.on = true;
		}
	}

	@Override
	protected void handleFailedAsync(Exception e) {
		if (this.toggler != null)
			this.toggler.cancel(true);

		logger.error("Execution of Toggler failed", e);
	}
}
