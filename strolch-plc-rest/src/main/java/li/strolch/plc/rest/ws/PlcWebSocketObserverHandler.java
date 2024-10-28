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

package li.strolch.plc.rest.ws;

import com.google.gson.JsonObject;
import li.strolch.agent.api.ObserverHandler;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.StrolchRootElement;
import li.strolch.websocket.WebSocketClient;
import li.strolch.websocket.WebSocketObserverHandler;

import java.util.Set;

import static li.strolch.plc.model.PlcConstants.TYPE_PLC_ADDRESS;
import static li.strolch.plc.rest.PlcModelVisitor.plcAddressToJson;

public class PlcWebSocketObserverHandler extends WebSocketObserverHandler {

	public PlcWebSocketObserverHandler(StrolchAgent agent, String realmName, ObserverHandler observerHandler,
			WebSocketClient client) {
		super(agent, realmName, observerHandler, client);
	}

	@Override
	protected boolean filter(Set<String> observedTypesSet, StrolchRootElement e) {
		return e.isResource() && e.getType().equals(TYPE_PLC_ADDRESS);
	}

	@Override
	protected JsonObject toJson(StrolchRootElement e) {
		if (e.isResource() && e.getType().equals(TYPE_PLC_ADDRESS))
			return e.accept(plcAddressToJson(false));
		return super.toJson(e);
	}
}
