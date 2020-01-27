package li.strolch.plc.core.service;

import static li.strolch.plc.core.PlcConstants.TYPE_PLC_CONNECTION;

import li.strolch.plc.core.ConnectionState;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.hw.PlcConnection;
import li.strolch.model.Resource;
import li.strolch.model.Tags;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

public class SetPlcConnectionStateService extends AbstractService<StringMapArgument, ServiceResult> {

	@Override
	protected ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	@Override
	public StringMapArgument getArgumentInstance() {
		return new StringMapArgument();
	}

	@Override
	protected ServiceResult internalDoService(StringMapArgument arg) throws Exception {

		String id = arg.map.get(Tags.Json.ID);
		String stateS = arg.map.get(Tags.Json.STATE);
		DBC.PRE.assertNotEmpty("id must be set!", id);
		DBC.PRE.assertNotEmpty("state must be set!", stateS);

		ConnectionState state = ConnectionState.valueOf(stateS);
		if (state != ConnectionState.Connected && state != ConnectionState.Disconnected)
			throw new IllegalArgumentException(
					"Only " + ConnectionState.Connected + " and " + ConnectionState.Disconnected + " states allowed!");

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {

			Resource connectionRes = tx.getResourceBy(TYPE_PLC_CONNECTION, id, true);
			tx.assertHasPrivilege(Operation.UPDATE, connectionRes);

			PlcHandler plcHandler = getComponent(PlcHandler.class);
			PlcConnection plcConnection = plcHandler.getPlc().getConnection(connectionRes.getId());

			if (state == ConnectionState.Connected)
				plcConnection.connect();
			else
				plcConnection.disconnect();

			return ServiceResult.success();
		}
	}
}
