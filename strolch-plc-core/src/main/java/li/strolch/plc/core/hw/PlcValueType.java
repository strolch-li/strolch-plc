package li.strolch.plc.core.hw;

import com.google.gson.JsonPrimitive;
import li.strolch.model.StrolchValueType;
import li.strolch.utils.helper.StringHelper;

public enum PlcValueType {
	Boolean() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(((Boolean) value).toString());
		}

		@Override
		public Object parseStringValue(String value) {
			return StringHelper.parseBoolean(value);
		}
	},
	Short() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(((Short) value).toString());
		}

		@Override
		public Object parseStringValue(String value) {
			return java.lang.Short.parseShort(value);
		}
	},
	Integer() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(((Integer) value).toString());
		}

		@Override
		public Object parseStringValue(String value) {
			return java.lang.Integer.parseInt(value);
		}
	},
	Long() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(value.toString());
		}

		@Override
		public Object parseStringValue(String value) {
			return java.lang.Long.parseLong(value);
		}
	},
	Float() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(value.toString());
		}

		@Override
		public Object parseStringValue(String value) {
			return java.lang.Float.parseFloat(value);
		}
	},
	Double() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(value.toString());
		}

		@Override
		public Object parseStringValue(String value) {
			return java.lang.Double.parseDouble(value);
		}
	},
	String() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive((String) value);
		}

		@Override
		public Object parseStringValue(String value) {
			return value;
		}
	},
	ByteArray() {
		@Override
		public JsonPrimitive valueToJson(Object value) {
			return new JsonPrimitive(StringHelper.toHexString((byte[]) value));
		}

		@Override
		public Object parseStringValue(String value) {
			return StringHelper.fromHexString(value);
		}
	};

	public abstract JsonPrimitive valueToJson(Object value);

	public abstract Object parseStringValue(String value);

	public static PlcValueType fromStrolchValueType(StrolchValueType valueType) {
		switch (valueType) {

		case BOOLEAN:
			return Boolean;
		case INTEGER:
			return Integer;
		case FLOAT:
			return Float;
		case LONG:
			return Long;
		case STRING:
		case TEXT:
			return String;
		default:
			throw new IllegalStateException("Unhandled strolch value type " + valueType);
		}
	}
}
