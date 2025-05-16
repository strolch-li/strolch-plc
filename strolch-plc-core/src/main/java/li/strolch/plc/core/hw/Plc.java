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

package li.strolch.plc.core.hw;

import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.utils.ExecutorPool;

import java.util.Set;
import java.util.stream.Stream;

public interface Plc {

	void start();

	void stop();

	Stream<PlcAddressKey> getAddressKeysStream();

	Set<PlcAddressKey> getAddressKeys();

	void setGlobalListener(PlcListener listener);

	void register(PlcAddress address, PlcListener listener);

	void unregister(PlcAddress address, PlcListener listener);

	void syncNotify(String address, Object value);

	void queueNotify(String address, Object value);

	void send(PlcAddress address);

	void send(PlcAddress address, boolean catchExceptions, boolean notifyGlobalListener);

	void send(PlcAddress address, Object value);

	void send(PlcAddress address, Object value, boolean catchExceptions, boolean notifyGlobalListener);

	void addConnection(PlcConnection connection);

	PlcConnection getConnection(String id);

	PlcConnection getConnection(PlcAddress address);

	void registerNotificationMapping(PlcAddress address);

	void notifyConnectionStateChanged(PlcConnection connection);

	void setConnectionStateChangeListener(PlcConnectionStateChangeListener listener);

	void setVerbose(boolean verbose);

	ExecutorPool getExecutorPool();
}
