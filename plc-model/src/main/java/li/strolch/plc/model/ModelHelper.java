package li.strolch.plc.model;

import com.google.gson.JsonPrimitive;

public class ModelHelper {

	public static JsonPrimitive valueToJson(Object value) {
		if (value instanceof Boolean)
			return new JsonPrimitive((Boolean) value);
		else if (value instanceof Number)
			return new JsonPrimitive((Number) value);
		else if (value instanceof String)
			return new JsonPrimitive((String) value);
		throw new IllegalArgumentException(
				"Unhandled value type " + (value == null ? "(null)" : value.getClass().getName()));
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
