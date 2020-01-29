package li.strolch.plc.rest;

import static java.util.Comparator.comparing;
import static li.strolch.rest.StrolchRestfulConstants.DATA;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.core.search.PlcVirtualAddressesSearch;
import li.strolch.plc.core.service.SendPlcAddressActionService;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.JsonServiceArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

@Path("plc/addresses")
public class PlcAddresses {

	@GET
	@Path("virtual")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVirtualAddresses(@Context HttpServletRequest request, @QueryParam("query") String query) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		PrivilegeContext ctx = RestfulStrolchComponent.getInstance().getPrivilegeHandler().validate(cert);

		JsonArray result = new PlcVirtualAddressesSearch()
				.search(RestfulStrolchComponent.getInstance().getAgent().getContainer(), ctx)
				.orderBy(comparing((PlcAddress p) -> p.resource).thenComparing(p -> p.action))
				.map(PlcModelVisitor::plcAddressToJson).asStream()
				.collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

		return ResponseUtil.toResponse(DATA, result);
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response sendAddressAction(@Context HttpServletRequest request, String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		JsonObject jsonObject = new JsonParser().parse(data).getAsJsonObject();

		SendPlcAddressActionService svc = new SendPlcAddressActionService();
		JsonServiceArgument arg = svc.getArgumentInstance();
		arg.jsonElement = jsonObject;

		// call service
		ServiceHandler svcHandler = RestfulStrolchComponent.getInstance().getServiceHandler();
		ServiceResult svcResult = svcHandler.doService(cert, svc, arg);
		return ResponseUtil.toResponse(svcResult);
	}
}
