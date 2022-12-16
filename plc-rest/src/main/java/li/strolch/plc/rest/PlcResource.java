package li.strolch.plc.rest;

import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.rest.StrolchRestfulConstants.DATA;

import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.service.SetPlcStateService;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

@Path("plc")
public class PlcResource {

	@GET
	@Path("state")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getState(@Context HttpServletRequest request) {
		PlcHandler plcHandler = RestfulStrolchComponent.getInstance().getComponent(PlcHandler.class);

		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty(PARAM_STATE, plcHandler.getPlcState().name());
		jsonObject.addProperty(PARAM_STATE_MSG, plcHandler.getPlcStateMsg());
		if (plcHandler.getPlc() != null)
			jsonObject.addProperty(PARAM_CLASS_NAME, plcHandler.getPlc().getClass().getName());
		else
			jsonObject.addProperty(PARAM_CLASS_NAME, "unknown");
		return ResponseUtil.toResponse(DATA, jsonObject);
	}

	@PUT
	@Path("state/{state}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response setState(@Context HttpServletRequest request, @PathParam("state") String state) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		SetPlcStateService svc = new SetPlcStateService();
		StringMapArgument arg = svc.getArgumentInstance();
		arg.map.put(PARAM_STATE, state);

		// call service
		ServiceHandler svcHandler = RestfulStrolchComponent.getInstance().getServiceHandler();
		ServiceResult svcResult = svcHandler.doService(cert, svc, arg);
		return ResponseUtil.toResponse(svcResult);
	}
}
