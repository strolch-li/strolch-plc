package li.strolch.plc.core.search;

import li.strolch.plc.model.PlcConstants;
import li.strolch.search.ResourceSearch;

import static li.strolch.utils.helper.StringHelper.isEmpty;

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
