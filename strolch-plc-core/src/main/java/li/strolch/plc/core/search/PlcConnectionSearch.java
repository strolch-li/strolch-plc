/*
 * Copyright (c) 2024 Robert von Burg <eitch@eitchnet.ch>
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
