package li.strolch.plc.rest;

import static java.util.Comparator.comparing;
import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;
import static li.strolch.plc.model.PlcConstants.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import li.strolch.model.Tags;
import li.strolch.model.json.StrolchRootElementToJsonVisitor;
import li.strolch.model.parameter.Parameter;
import li.strolch.model.visitor.ResourceVisitor;
import li.strolch.plc.model.PlcAddress;

public class PlcModelVisitor {

	public static StrolchRootElementToJsonVisitor toJson() {
		return new StrolchRootElementToJsonVisitor().withoutPolicies();
	}

	public static StrolchRootElementToJsonVisitor toJsonFlat() {
		return toJson().flat().withoutVersion();
	}

	public static StrolchRootElementToJsonVisitor plcConnectionToJson() {
		return toJsonFlat().resourceHook((connectionR, connectionJ) -> {

			// add the custom parameters with keys for the id, name and value, so we can show them on the UI
			JsonArray parametersJ = new JsonArray();
			connectionR
					.getParameterBag(BAG_PARAMETERS, true)
					.getParameters()
					.stream()
					.sorted(comparing(Parameter::getIndex))
					.filter(p -> !(
							p.getId().equals(PARAM_STATE) || p.getId().equals(PARAM_STATE_MSG) || p
									.getId()
									.equals(PARAM_CLASS_NAME)))
					.forEach(parameter -> {
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

	public static ResourceVisitor<JsonObject> plcAddressToJson(boolean simple) {
		if (simple)
			return telegram -> {
				JsonObject telegramJ = new JsonObject();
				telegramJ.addProperty(PARAM_ADDRESS, telegram.getString(PARAM_ADDRESS));
				telegramJ.addProperty(PARAM_RESOURCE, telegram.getString(PARAM_RESOURCE));
				telegramJ.addProperty(PARAM_VALUE_TYPE, telegram.getParameter(PARAM_VALUE).getValueType().getType());
				telegramJ.addProperty(PARAM_ACTION, telegram.getString(PARAM_ACTION));
				telegramJ.addProperty(PARAM_VALUE, telegram.getParameter(PARAM_VALUE, true).getValueAsString());
				return telegramJ;
			};

		return toJsonFlat().resourceHook((address, addressJ) -> {
			addressJ.addProperty(PARAM_VALUE_TYPE, address.getParameter(PARAM_VALUE, true).getValueType().getType());
		}).asResourceVisitor();
	}

	public static ResourceVisitor<JsonObject> plcTelegramToJson(boolean simple) {
		if (simple)
			return telegram -> {
				JsonObject telegramJ = new JsonObject();
				telegramJ.addProperty(PARAM_RESOURCE, telegram.getString(PARAM_RESOURCE));
				telegramJ.addProperty(PARAM_ACTION, telegram.getString(PARAM_ACTION));
				telegramJ.addProperty(PARAM_VALUE, telegram.getParameter(PARAM_VALUE, true).getValueAsString());
				return telegramJ;
			};

		return toJsonFlat().resourceHook((address, addressJ) -> {
			addressJ.addProperty(PARAM_VALUE_TYPE, address.getParameter(PARAM_VALUE).getValueType().getType());
		}).asResourceVisitor();
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
