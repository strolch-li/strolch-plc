package li.strolch.plc.gw.server;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;

import li.strolch.rest.RestfulStrolchComponent;

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