package li.strolch.plc.gw.server;

import javax.websocket.*;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchComponent;

public class PlcGwServerHandler extends StrolchComponent {

	public PlcGwServerHandler(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	public void onMessage(String message, Session session) {
		logger.info(session.getId() + ": Message: " + message);
	}

	public void onPong(PongMessage message, Session session) {
		logger.info(session.getId() + ": Message: " + message);
	}

	public void onOpen(Session session) {
		logger.info(session.getId() + ": Opening!");
	}

	public void onClose(Session session, CloseReason closeReason) {
		logger.info(session.getId() + ": Closing: " + closeReason);
	}

	public void onError(Session session, Throwable throwable) {
		logger.error(session.getId() + ": Error: " + throwable.getMessage(), true);
	}
}
