package li.strolch.plc.core.search;

import li.strolch.plc.model.PlcConstants;
import li.strolch.search.ResourceSearch;

import static li.strolch.utils.helper.StringHelper.isEmpty;

public class PlcConnectionSearch extends ResourceSearch {

	public PlcConnectionSearch() {
		types(PlcConstants.TYPE_PLC_CONNECTION);
	}

	public PlcConnectionSearch stringQuery(String value) {
		if (isEmpty(value))
			return this;

		value = value.trim();
		String[] values = value.split(" ");
		where(id().containsIgnoreCase(values) //
				.or(name().containsIgnoreCase(values)));

		return this;
	}
}
