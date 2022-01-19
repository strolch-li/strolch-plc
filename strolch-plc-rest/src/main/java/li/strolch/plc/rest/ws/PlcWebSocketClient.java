package li.strolch.plc.rest.ws;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import li.strolch.agent.api.ObserverHandler;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.websocket.WebSocketClient;
import li.strolch.websocket.WebSocketObserverHandler;

public class PlcWebSocketClient extends WebSocketClient {

	public PlcWebSocketClient(StrolchAgent agent, Session session, EndpointConfig config) {
		super(agent, session, config);
	}

	@Override
	protected WebSocketObserverHandler getWebSocketObserverHandler(String realm, ObserverHandler observerHandler) {
		return new PlcWebSocketObserverHandler(this.agent, realm, observerHandler, this);
	}
}
