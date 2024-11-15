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

package li.strolch.plc.gw.server;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import li.strolch.rest.RestfulStrolchComponent;

import java.io.IOException;

@ServerEndpoint("/websocket/strolch/plc")
public class PlcServerWebSocketEndpoint {

	private final PlcGwServerHandler serverHandler;

	public PlcServerWebSocketEndpoint() {
		this.serverHandler = RestfulStrolchComponent.getInstance().getComponent(PlcGwServerHandler.class);
	}

	@OnMessage
	public void onMessage(String message, Session session) throws IOException {
		this.serverHandler.onWsMessage(message, session);
	}

	@OnMessage
	public void onPong(PongMessage message, Session session) {
		this.serverHandler.onWsPong(message, session);
	}

	@OnOpen
	public void onOpen(Session session) {
		this.serverHandler.onWsOpen(session);
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		this.serverHandler.onWsClose(session, closeReason);
	}

	@OnError
	public void onError(Session session, Throwable throwable) {
		this.serverHandler.onWsError(session, throwable);
	}
}