package li.strolch.plc.model;

import java.util.Objects;

import li.strolch.model.StrolchValueType;

public class PlcAddress {

	public final PlcAddressType type;

	public final String resource;
	public final String action;

	public final String address;
	public final StrolchValueType valueType;
	public final Object defaultValue;
	public final boolean inverted;

	public PlcAddress(PlcAddressType type, String resource, String action, String address, StrolchValueType valueType,
			Object defaultValue, boolean inverted) {
		this.type = type;
		this.resource = resource.intern();
		this.action = action.intern();
		this.address = address.intern();
		this.valueType = valueType;
		this.defaultValue = defaultValue;
		this.inverted = inverted;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		PlcAddress that = (PlcAddress) o;

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

	@Override
	public String toString() {
		return this.type + " " + this.resource + "-" + this.action + " " + this.valueType.getType() + " @ "
				+ this.address;
	}

	public String toKey() {
		return this.resource + "-" + this.action;
	}

	public String toKeyAddress() {
		return this.resource + "-" + this.action + " @ " + this.address;
	}
}
