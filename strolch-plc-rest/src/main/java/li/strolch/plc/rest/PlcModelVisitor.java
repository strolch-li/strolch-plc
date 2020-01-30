package li.strolch.plc.rest;

import static li.strolch.plc.model.PlcConstants.*;
import static java.util.Comparator.comparing;
import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;

import li.strolch.plc.model.PlcAddress;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import li.strolch.model.Tags;
import li.strolch.model.json.StrolchRootElementToJsonVisitor;
import li.strolch.model.parameter.Parameter;

public class PlcModelVisitor {

	public static StrolchRootElementToJsonVisitor toJson() {
		return new StrolchRootElementToJsonVisitor().withoutPolicies();
	}

	public static StrolchRootElementToJsonVisitor toJsonFlat() {
		return toJson().flat();
	}

	public static StrolchRootElementToJsonVisitor plcConnectionToJson() {
		return toJsonFlat().resourceHook((connectionR, connectionJ) -> {

			// add the custom parameters with keys for the id, name and value, so we can show them on the UI
			JsonArray parametersJ = new JsonArray();
			connectionR.getParameterBag(BAG_PARAMETERS, true).getParameters().stream()
					.sorted(comparing(Parameter::getIndex))
					.filter(p -> !(p.getId().equals(PARAM_STATE) || p.getId().equals(PARAM_STATE_MSG) || p.getId()
							.equals(PARAM_CLASS_NAME))).forEach(parameter -> {
				JsonObject paramJ = new JsonObject();
				paramJ.addProperty(Tags.Json.ID, parameter.getId());
				paramJ.addProperty(Tags.Json.NAME, parameter.getName());
				paramJ.addProperty(Tags.Json.VALUE, parameter.getValueAsString());
				parametersJ.add(paramJ);
			});
			connectionJ.add(BAG_PARAMETERS, parametersJ);
		});
	}

	public static StrolchRootElementToJsonVisitor plcLogicalDeviceToJson() {
		return toJsonFlat();
	}

	public static StrolchRootElementToJsonVisitor plcAddressToJson() {
		return toJsonFlat().resourceHook((address, addressJ) -> {
			addressJ.addProperty(PARAM_VALUE_TYPE, address.getParameter(PARAM_VALUE).getValueType().getType());
		});
	}

	public static StrolchRootElementToJsonVisitor plcTelegramToJson() {
		return toJsonFlat().resourceHook((address, addressJ) -> {
			addressJ.addProperty(PARAM_VALUE_TYPE, address.getParameter(PARAM_VALUE).getValueType().getType());
		});
	}

	public static JsonObject plcAddressToJson(PlcAddress plcAddress) {
		JsonObject addressJ = new JsonObject();

		addressJ.addProperty(PARAM_ADDRESS, plcAddress.address);
		addressJ.addProperty(PARAM_RESOURCE, plcAddress.resource);
		addressJ.addProperty(PARAM_ACTION, plcAddress.action);
		addressJ.addProperty(PARAM_ADDRESS_TYPE, plcAddress.type.name());
		addressJ.addProperty(Tags.Json.ID, plcAddress.address);
		addressJ.add(Tags.Json.VALUE, plcAddress.valueType.valueToJson(plcAddress.defaultValue));
		addressJ.addProperty(Tags.Json.TYPE, plcAddress.valueType.name());
		addressJ.addProperty(PARAM_VALUE_TYPE, plcAddress.valueType.name());

		return addressJ;
	}
}
