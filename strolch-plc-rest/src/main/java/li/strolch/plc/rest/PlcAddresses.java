package li.strolch.plc.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import li.strolch.plc.core.service.SendPlcAddressActionService;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.JsonServiceArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

@Path("plc/addresses")
public class PlcAddresses {

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response sendAddressAction(@Context HttpServletRequest request, String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		JsonObject jsonObject = JsonParser.parseString(data).getAsJsonObject();

		SendPlcAddressActionService svc = new SendPlcAddressActionService();
		JsonServiceArgument arg = svc.getArgumentInstance();
		arg.jsonElement = jsonObject;

		// call service
		ServiceHandler svcHandler = RestfulStrolchComponent.getInstance().getServiceHandler();
		ServiceResult svcResult = svcHandler.doService(cert, svc, arg);
		return ResponseUtil.toResponse(svcResult);
	}
}
