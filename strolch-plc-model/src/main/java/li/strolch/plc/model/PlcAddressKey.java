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
