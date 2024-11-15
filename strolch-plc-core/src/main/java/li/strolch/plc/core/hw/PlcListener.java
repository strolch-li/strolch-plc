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

package li.strolch.plc.core.hw;

import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.PlcAddress;

/**
 * The interface which can be notified by the {@link PlcHandler} when an event is detected from the hardware.
 */
public interface PlcListener {

	/**
	 * Notifies the listener of the new value at the given address
	 *
	 * @param address the address at which the event was detected
	 * @param value   the new value at the address
	 */
	void handleNotification(PlcAddress address, Object value);
}
