package li.strolch.plc.model;

import java.util.Objects;

public class PlcAddressKey {
	public final String resource;
	public final String action;

	public PlcAddressKey(String resource, String action) {
		this.resource = resource;
		this.action = action;
	}

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
}
