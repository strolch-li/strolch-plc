package li.strolch.plc.gw;

import javax.websocket.*;

@ClientEndpoint
public class PlcGwClientEndpoint {

	private PlcGwHandler gwHandler;

	public PlcGwClientEndpoint(PlcGwHandler gwHandler) {
		this.gwHandler = gwHandler;
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		this.gwHandler.handleMessage(session, message);
	}

	@OnMessage
	public void onPong(PongMessage message, Session session) {
		this.gwHandler.pong(message, session);
	}

	@OnOpen
	public void onOpen(Session session) {
		this.gwHandler.open(session);
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		this.gwHandler.close(session, closeReason);
	}

	@OnError
	public void onError(Session session, Throwable throwable) {
		this.gwHandler.error(session, throwable);
	}
}