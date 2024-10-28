package li.strolch.plc.gw.server.service;

import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.PlcAddressResponse;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import static li.strolch.plc.model.PlcConstants.*;

public class SendPlcTelegramService extends AbstractService<StringMapArgument, ServiceResult> {

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

		String plcId = arg.map.get(PARAM_PLC_ID);
		String resource = arg.map.get(PARAM_RESOURCE);
		String action = arg.map.get(PARAM_ACTION);

		DBC.PRE.assertNotEmpty(PARAM_PLC_ID + " must be set!", plcId);
		DBC.PRE.assertNotEmpty(PARAM_RESOURCE + " must be set!", resource);
		DBC.PRE.assertNotEmpty(PARAM_ACTION + " must be set!", action);

		try (StrolchTransaction tx = openArgOrUserTx(arg, false)) {

			SendPlcTelegramCommand cmd = new SendPlcTelegramCommand(tx);
			cmd.setPlcId(plcId);
			cmd.setResource(resource);
			cmd.setAction(action);

			if (!arg.map.containsKey(PARAM_VALUE)) {
				String value = arg.map.get(PARAM_VALUE);
				String valueType = arg.map.get(PARAM_VALUE_TYPE);
				DBC.PRE.assertNotEmpty(PARAM_VALUE + " must either not be set, or a non-empty value", value);
				DBC.PRE.assertNotEmpty(PARAM_VALUE_TYPE + " must be set when a value is given!", valueType);

				cmd.setValue(value);
				cmd.setValueType(valueType);
			}

			cmd.validate();
			cmd.doCommand();

			PlcAddressResponse response;
			response = cmd.getResponse();
			if (response.isFailed()) {
				tx.rollbackOnClose();
				return ServiceResult.error(response.getState() + ": " + response.getStateMsg());
			}

			if (tx.needsCommit())
				tx.commitOnClose();
		}

		return ServiceResult.success();
	}
}
