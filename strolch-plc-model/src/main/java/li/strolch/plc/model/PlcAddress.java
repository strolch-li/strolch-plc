package li.strolch.plc.model;

import li.strolch.model.StrolchValueType;

import java.util.Objects;

import static java.text.MessageFormat.format;

/**
 * <p>This represents the address on the PLC. Addresses always consists of a resource and action field. This address
 * includes:</p>
 * <ul>
 *     <li>the type defined as {@link PlcAddressType}</li>
 *     <li>remote flag, denoting of this address should be sent to a remote listener</li>
 *     <li>key field as a standard concatenation of resource and action</li>
 *     <li>key address as a standard representation for logging</li>
 *     <li>address being the hardware address of this address. I.e. the hardware address to which the resource and action fields are pointing</li>
 *     <li>{@link StrolchValueType} for this address</li>
 *     <li>the default value for the action and resource for when sending and notifying</li>
 *     <li>inverted flag to define if a boolean value should be inverted. This is done by the system and the user need not invert as well</li>
 * </ul>
 *
 * @see PlcAddressKey
 */
public class PlcAddress {

	public final PlcAddressType type;

	public final String resource;
	public final String action;
	public final PlcAddressKey plcAddressKey;
	public final boolean remote;

	/**
	 * Format: <code>resource</code>-<code>action</code>
	 */
	public final String key;
	/**
	 * Format: <code>resource</code>-<code>action</code> @ <code>address</code>
	 */
	public final String keyAddress;

	/**
	 * Actual hardware address
	 */
	public final String address;

	public final StrolchValueType valueType;
	public final Object defaultValue;
	public final boolean inverted;

	public PlcAddress(PlcAddressType type, String resource, String action, String address, StrolchValueType valueType,
			Object defaultValue, boolean inverted, boolean remote) {
		this.type = type;
		this.resource = resource.intern();
		this.action = action.intern();
		this.address = address.intern();

		this.plcAddressKey = PlcAddressKey.keyFor(this.resource, this.action);
		this.key = this.resource + "-" + this.action;
		this.keyAddress = this.resource + "-" + this.action + " @ " + this.address;

		this.valueType = valueType;
		this.defaultValue = defaultValue;
		this.inverted = inverted;

		this.remote = remote;
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
		return format("{0} {1}-{2} {3} @ {4}", this.type, this.resource, this.action, this.valueType.getType(),
				this.address);
	}

	public String toKey() {
		return this.key;
	}

	public String toKeyAddress() {
		return this.keyAddress;
	}

	public PlcAddressKey toPlcAddressKey() {
		return this.plcAddressKey;
	}
}
