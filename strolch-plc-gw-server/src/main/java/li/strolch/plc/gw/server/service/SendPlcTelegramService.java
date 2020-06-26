package li.strolch.plc.gw.server.service;

import static li.strolch.plc.model.PlcConstants.*;

import com.google.gson.JsonPrimitive;
import li.strolch.model.StrolchValueType;
import li.strolch.plc.gw.server.PlcGwServerHandler;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressResponse;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

public class SendPlcTelegramService extends AbstractService<StringMapArgument, ServiceResult> {

	@Override
	protected ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	@Override
	public StringMapArgument getArgumentInstance() {
		return new StringMapArgument();
	}

	@Override
	protected ServiceResult internalDoService(StringMapArgument arg) throws Exception {

		String plcId = arg.map.get(PARAM_PLC_ID);
		String resource = arg.map.get(PARAM_RESOURCE);
		String action = arg.map.get(PARAM_ACTION);

		DBC.PRE.assertNotEmpty(PARAM_PLC_ID + " must be set!", plcId);
		DBC.PRE.assertNotEmpty(PARAM_RESOURCE + " must be set!", resource);
		DBC.PRE.assertNotEmpty(PARAM_ACTION + " must be set!", action);

		PlcGwServerHandler plcHandler = getComponent(PlcGwServerHandler.class);

		PlcAddressResponse response;
		if (!arg.map.containsKey(PARAM_VALUE)) {
			response = plcHandler.sendMessageSync(PlcAddressKey.keyFor(resource, action), plcId);
		} else {

			String valueS = arg.map.get(PARAM_VALUE);
			String valueTypeS = arg.map.get(PARAM_VALUE_TYPE);

			DBC.PRE.assertNotEmpty(PARAM_VALUE + " must either not be set, or a non-empty value", valueS);
			DBC.PRE.assertNotEmpty(PARAM_VALUE_TYPE + " must be set when a value is given!", valueTypeS);

			StrolchValueType valueType = StrolchValueType.parse(valueTypeS);
			Object value = valueType.parseValue(valueS);
			JsonPrimitive valueJ;
			if (value instanceof String)
				valueJ = new JsonPrimitive((String) value);
			else if (value instanceof Number)
				valueJ = new JsonPrimitive((Number) value);
			else if (value instanceof Boolean)
				valueJ = new JsonPrimitive((Boolean) value);
			else
				throw new IllegalArgumentException("Unhandled value type " + valueType);

			response = plcHandler.sendMessageSync(PlcAddressKey.keyFor(resource, action), plcId, valueJ);
		}

		if (response.isDone())
			return ServiceResult.success();

		return ServiceResult.error(response.getState() + ": " + response.getStateMsg());
	}
}
