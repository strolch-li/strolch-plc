package li.strolch.plc.core.service;

import static li.strolch.plc.model.PlcConstants.*;

import com.google.gson.JsonObject;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.service.JsonServiceArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

public class SendPlcAddressActionService extends AbstractService<JsonServiceArgument, ServiceResult> {
	@Override
	protected ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	@Override
	public JsonServiceArgument getArgumentInstance() {
		return new JsonServiceArgument();
	}

	@Override
	protected ServiceResult internalDoService(JsonServiceArgument arg) throws Exception {

		JsonObject jsonObject = arg.jsonElement.getAsJsonObject();
		PlcAddressType addressType = PlcAddressType.valueOf(jsonObject.get(PARAM_TYPE).getAsString());
		String resource = jsonObject.get(PARAM_RESOURCE).getAsString();
		String action = jsonObject.get(PARAM_ACTION).getAsString();

		PlcHandler plcHandler = getComponent(PlcHandler.class);
		PlcAddress plcAddress = plcHandler.getPlcAddress(resource, action);

		if (addressType == PlcAddressType.Telegram) {
			if (jsonObject.has(PARAM_VALUE)) {
				String valueS = jsonObject.get(PARAM_VALUE).getAsString();
				Object value = plcAddress.valueType.parseValue(valueS);
				logger.info("PLC Send " + resource + "-" + action + " with " + valueS);
				plcHandler.send(resource, action, value);
			} else {
				logger.info("PLC Send " + resource + "-" + action + " with default value " + plcAddress.defaultValue);
				plcHandler.send(resource, action);
			}
		} else if (addressType == PlcAddressType.Notification) {
			if (!jsonObject.has(PARAM_VALUE))
				throw new IllegalArgumentException("For notification a value must be set!");

			String valueS = jsonObject.get(PARAM_VALUE).getAsString();
			Object value = plcAddress.valueType.parseValue(valueS);

			logger.info("PLC Notification " + resource + "-" + action + " with " + valueS);
			plcHandler.notify(resource, action, value);

		} else {
			throw new UnsupportedOperationException("Unhandled address type " + addressType);
		}

		return ServiceResult.success();
	}
}
