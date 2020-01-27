package li.strolch.plc.rest.ws;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.ObserverHandler;
import li.strolch.websocket.WebSocketClient;
import li.strolch.websocket.WebSocketObserverHandler;

public class PlcWebSocketClient extends WebSocketClient {

	public PlcWebSocketClient(ComponentContainer container, Session session, EndpointConfig config) {
		super(container, session, config);
	}

	@Override
	protected WebSocketObserverHandler getWebSocketObserverHandler(ObserverHandler observerHandler) {
		return new PlcWebSocketObserverHandler(observerHandler, this);
	}
}
