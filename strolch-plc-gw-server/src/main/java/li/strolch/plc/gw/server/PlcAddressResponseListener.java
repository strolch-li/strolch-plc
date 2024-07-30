package li.strolch.plc.gw.server;

import li.strolch.plc.model.PlcAddressResponse;

public interface PlcAddressResponseListener {

	void handleResponse(PlcAddressResponse response) throws Exception;
}
