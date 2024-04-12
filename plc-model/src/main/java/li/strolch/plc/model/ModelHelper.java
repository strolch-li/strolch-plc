package li.strolch.plc.model;

import com.google.gson.JsonPrimitive;

public class ModelHelper {

	public static JsonPrimitive valueToJson(Object value) {
		return switch (value) {
			case Boolean b -> new JsonPrimitive(b);
			case Number number -> new JsonPrimitive(number);
			case String s -> new JsonPrimitive(s);
			case null, default -> throw new IllegalArgumentException(
					"Unhandled value type " + (value == null ? "(null)" : value.getClass().getName()));
		};
	}

	public static Object jsonToValue(JsonPrimitive valueJ) {
		if (valueJ.isBoolean())
			return valueJ.getAsBoolean();
		else if (valueJ.isNumber())
			return valueJ.getAsNumber();
		else if (valueJ.isString())
			return valueJ.getAsString();
		throw new IllegalArgumentException("Unhandled value type " + valueJ);
	}
}
