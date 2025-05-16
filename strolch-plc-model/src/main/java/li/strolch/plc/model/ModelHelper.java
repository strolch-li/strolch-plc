/*
 * Copyright (c) 2013-2025 Robert von Burg <eitch@eitchnet.ch>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
