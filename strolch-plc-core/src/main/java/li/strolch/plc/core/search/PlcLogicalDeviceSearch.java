package li.strolch.plc.core.search;

import static li.strolch.utils.helper.StringHelper.isEmpty;

import li.strolch.plc.core.PlcConstants;
import li.strolch.search.ResourceSearch;

public class PlcLogicalDeviceSearch extends ResourceSearch {

	public PlcLogicalDeviceSearch() {
		types(PlcConstants.TYPE_PLC_LOGICAL_DEVICE);
	}

	public PlcLogicalDeviceSearch stringQuery(String value) {
		if (isEmpty(value))
			return this;

		value = value.trim();
		String[] values = value.split(" ");
		where(id().containsIgnoreCase(values) //
				.or(name().containsIgnoreCase(values)));

		return this;
	}
}
