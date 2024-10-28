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

package li.strolch.plc.core;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.model.Locator;
import li.strolch.model.log.LogMessage;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.Plc;
import li.strolch.plc.core.hw.PlcListener;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcState;
import li.strolch.privilege.model.Certificate;

public interface PlcHandler {

	ComponentContainer getContainer();

	String getPlcId();

	PlcState getPlcState();

	String getPlcStateMsg();

	boolean reconfigurePlc();

	void startPlc();

	void stopPlc();

	Plc getPlc();

	PlcAddress getPlcAddress(String resource, String action);

	String getPlcAddressId(String resource, String action);

	void setGlobalListener(GlobalPlcListener listener);

	void register(String resource, String action, PlcListener listener);

	void unregister(String resource, String action, PlcListener listener);

	void send(String resource, String action);

	void send(String resource, String action, boolean catchExceptions, boolean notifyGlobalListener);

	void send(String resource, String action, Object value);

	void send(String resource, String action, Object value, boolean catchExceptions, boolean notifyGlobalListener);

	void notify(String resource, String action, Object value);

	void sendMsg(LogMessage message);

	void disableMsg(Locator locator);

	StrolchTransaction openTx(Certificate cert, boolean readOnly);
}
