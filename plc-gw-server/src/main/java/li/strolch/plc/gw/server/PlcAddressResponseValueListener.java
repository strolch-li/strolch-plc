package li.strolch.plc.gw.server;

import li.strolch.plc.model.PlcAddressValueResponse;

public interface PlcAddressResponseValueListener {

	void handleResponse(PlcAddressValueResponse response) throws Exception;
}
