package li.strolch.plc.core.search;

import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.model.PlcAddress;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.search.SearchResult;
import li.strolch.search.StrolchValueSearch;

public class PlcVirtualAddressesSearch extends StrolchValueSearch<PlcAddress> {

	public SearchResult<PlcAddress> search(ComponentContainer container, PrivilegeContext ctx) {
		assertHasPrivilege(ctx);

		PlcHandler plcHandler = container.getComponent(PlcHandler.class);
		return new SearchResult<>(plcHandler.getVirtualAddresses().stream());
	}
}
