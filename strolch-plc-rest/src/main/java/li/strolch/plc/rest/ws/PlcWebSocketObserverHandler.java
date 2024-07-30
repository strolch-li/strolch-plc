package li.strolch.plc.rest.ws;

import static li.strolch.plc.model.PlcConstants.TYPE_PLC_ADDRESS;
import static li.strolch.plc.rest.PlcModelVisitor.plcAddressToJson;

import java.util.Set;

import com.google.gson.JsonObject;
import li.strolch.agent.api.ObserverHandler;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.StrolchRootElement;
import li.strolch.websocket.WebSocketClient;
import li.strolch.websocket.WebSocketObserverHandler;

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
