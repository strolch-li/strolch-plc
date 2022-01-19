package li.strolch.plc.rest.ws;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;

import li.strolch.agent.api.StrolchAgent;
import li.strolch.rest.RestfulStrolchComponent;

@ServerEndpoint("/websocket/plc/observer")
public class WebSocketEndpoint {

	private final ConcurrentHashMap<Session, PlcWebSocketClient> clientMap = new ConcurrentHashMap<>();

	@OnOpen
	public void onOpen(Session session, EndpointConfig config) {
		StrolchAgent agent = RestfulStrolchComponent.getInstance().getAgent();
		PlcWebSocketClient updateClient = new PlcWebSocketClient(agent, session, config);
		this.clientMap.put(session, updateClient);
		session.addMessageHandler(updateClient);
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		PlcWebSocketClient webSocketClient = this.clientMap.remove(session);
		if (webSocketClient != null)
			webSocketClient.close(closeReason);
	}

	@OnError
	public void onError(Session session, Throwable t) {
		PlcWebSocketClient webSocketClient = this.clientMap.get(session);
		if (webSocketClient != null)
			webSocketClient.onError(t);
	}
}