/*
 * Copyright (c) 2013-2024 Robert von Burg <eitch@eitchnet.ch>
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

import java.util.Objects;

/**
 * Defines a logical key to reference a {@link PlcAddress}. A key has two parts, the <code>resource</code> which is used
 * to reference a physical something, and the <code>action</code> to reference what part of the physical something is
 * being referenced, i.e. having its state changed
 */
public class PlcAddressKey {
	public final String resource;
	public final String action;

	private PlcAddressKey(String resource, String action) {
		this.resource = resource;
		this.action = action;
	}

	/**
	 * Returns a string in the form <code>resource-action</code>
	 *
	 * @return a string in the form <code>resource-action</code>
	 */
	public String toKey() {
		return this.resource + '-' + this.action;
	}

	@Override
	public String toString() {
		return toKey();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		PlcAddressKey that = (PlcAddressKey) o;

		if (!Objects.equals(resource, that.resource))
			return false;
		return Objects.equals(action, that.action);
	}

	@Override
	public int hashCode() {
		int result = resource != null ? resource.hashCode() : 0;
		result = 31 * result + (action != null ? action.hashCode() : 0);
		return result;
	}

	public static PlcAddressKey keyFor(String resource, String action) {
		return new PlcAddressKey(resource, action);
	}

	public static PlcAddressKey parseKey(String key) {
		String[] parts = key.split("-");
		if (parts.length != 2)
			throw new IllegalStateException("Invalid key: " + key);
		return PlcAddressKey.keyFor(parts[0], parts[1]);
	}
}
