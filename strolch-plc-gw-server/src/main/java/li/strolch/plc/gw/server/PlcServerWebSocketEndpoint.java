package li.strolch.plc.gw.server;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import li.strolch.rest.RestfulStrolchComponent;

@ServerEndpoint("/websocket/strolch/plc")
public class PlcServerWebSocketEndpoint {

	private PlcGwServerHandler serverHandler;

	public PlcServerWebSocketEndpoint() {
		this.serverHandler = RestfulStrolchComponent.getInstance().getComponent(PlcGwServerHandler.class);
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		this.serverHandler.onMessage(message, session);
	}

	@OnMessage
	public void onPong(PongMessage message, Session session) {
		this.serverHandler.onPong(message, session);
	}

	@OnOpen
	public void onOpen(Session session) {
		this.serverHandler.onOpen(session);
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		this.serverHandler.onClose(session, closeReason);
	}

	@OnError
	public void onError(Session session, Throwable throwable) {
		this.serverHandler.onError(session, throwable);
	}
}