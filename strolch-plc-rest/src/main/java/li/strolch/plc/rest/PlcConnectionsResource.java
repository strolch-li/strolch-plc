package li.strolch.plc.rest;

import static li.strolch.plc.rest.PlcModelVisitor.plcConnectionToJson;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonObject;
import li.strolch.model.Tags;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.search.PlcConnectionSearch;
import li.strolch.plc.core.service.SetPlcConnectionStateService;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.StringMapArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.collections.Paging;

@Path("plc/connections")
public class PlcConnectionsResource {

	private static String getContext() {
		StackTraceElement element = new Throwable().getStackTrace()[1];
		return element.getClassName() + "." + element.getMethodName();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getConnections(@Context HttpServletRequest request, @QueryParam("query") String query,
			@QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("limit") @DefaultValue("20") int limit) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		Paging<JsonObject> paging;
		try (StrolchTransaction tx = RestfulStrolchComponent.getInstance().openTx(cert, getContext())) {
			paging = new PlcConnectionSearch() //
					.stringQuery(query) //
					.search(tx) //
					.orderByName() //
					.visitor(plcConnectionToJson()) //
					.toPaging(offset, limit);
		}

		return ResponseUtil.toResponse(paging);
	}

	@PUT
	@Path("{id}/state/{state}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response setState(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("state") String state) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		SetPlcConnectionStateService svc = new SetPlcConnectionStateService();
		StringMapArgument arg = svc.getArgumentInstance();
		arg.map.put(Tags.Json.ID, id);
		arg.map.put(Tags.Json.STATE, state);

		// call service
		ServiceHandler svcHandler = RestfulStrolchComponent.getInstance().getServiceHandler();
		ServiceResult svcResult = svcHandler.doService(cert, svc, arg);
		return ResponseUtil.toResponse(svcResult);
	}
}
