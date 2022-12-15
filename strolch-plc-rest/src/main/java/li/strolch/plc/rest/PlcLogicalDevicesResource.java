package li.strolch.plc.rest;

import static java.util.Comparator.comparing;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.plc.rest.PlcModelVisitor.*;
import static li.strolch.rest.StrolchRestfulConstants.DATA;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.model.Tags;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.search.PlcLogicalDeviceSearch;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.utils.collections.MapOfLists;

@Path("plc/logicalDevices")
public class PlcLogicalDevicesResource {

	private static String getContext() {
		StackTraceElement element = new Throwable().getStackTrace()[1];
		return element.getClassName() + "." + element.getMethodName();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLogicalDevices(@Context HttpServletRequest request, @QueryParam("query") String query) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		JsonArray dataJ = new JsonArray();
		try (StrolchTransaction tx = RestfulStrolchComponent.getInstance().openTx(cert, getContext())) {

			MapOfLists<String, Resource> devicesByGroup = new PlcLogicalDeviceSearch() //
					.stringQuery(query) //
					.search(tx) //
					.orderBy(comparing((Resource o) -> o.getParameter(PARAM_INDEX).getValue())) //
					.toMapOfLists(r -> r.hasParameter(PARAM_GROUP) ?
							r.getParameter(PARAM_GROUP).getValueAsString() :
							"default");

			Set<String> groups = new TreeSet<>(devicesByGroup.keySet());
			for (String group : groups) {
				List<Resource> devices = devicesByGroup.getList(group);
				JsonObject groupJ = new JsonObject();
				groupJ.addProperty(Tags.Json.NAME, group);
				groupJ.add(DATA, devices.stream()
						.map(e -> e.accept(plcLogicalDeviceToJson()))
						.collect(JsonArray::new, JsonArray::add, JsonArray::addAll));

				dataJ.add(groupJ);
			}
		}

		return ResponseUtil.toResponse(DATA, dataJ);
	}

	@GET
	@Path("{id}/addresses")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAddresses(@Context HttpServletRequest request, @PathParam("id") String id,
			@QueryParam("simple") boolean simple) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		JsonArray dataJ;
		try (StrolchTransaction tx = RestfulStrolchComponent.getInstance().openTx(cert, getContext())) {
			Resource plcLogicalDevice = tx.getResourceBy(TYPE_PLC_LOGICAL_DEVICE, id, true);
			tx.assertHasPrivilege(Operation.GET, plcLogicalDevice);

			dataJ = tx.getResourcesByRelation(plcLogicalDevice, PARAM_ADDRESSES, true)
					.stream()
					.map(e -> e.accept(plcAddressToJson(simple)))
					.collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
		}

		return ResponseUtil.toResponse(DATA, dataJ);
	}

	@GET
	@Path("{id}/notifications")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getNotifications(@Context HttpServletRequest request, @PathParam("id") String id,
			@QueryParam("simple") boolean simple) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		JsonArray dataJ;
		try (StrolchTransaction tx = RestfulStrolchComponent.getInstance().openTx(cert, getContext())) {
			Resource plcLogicalDevice = tx.getResourceBy(TYPE_PLC_LOGICAL_DEVICE, id, true);
			tx.assertHasPrivilege(Operation.GET, plcLogicalDevice);

			dataJ = tx.getResourcesByRelation(plcLogicalDevice, PARAM_ADDRESSES, true)
					.stream()
					.map(e -> e.accept(plcAddressToJson(simple)))
					.collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
		}

		return ResponseUtil.toResponse(DATA, dataJ);
	}

	@GET
	@Path("{id}/telegrams")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTelegrams(@Context HttpServletRequest request, @PathParam("id") String id,
			@QueryParam("simple") boolean simple) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		JsonArray dataJ;
		try (StrolchTransaction tx = RestfulStrolchComponent.getInstance().openTx(cert, getContext())) {
			Resource plcLogicalDevice = tx.getResourceBy(TYPE_PLC_LOGICAL_DEVICE, id, true);
			tx.assertHasPrivilege(Operation.GET, plcLogicalDevice);

			dataJ = tx.getResourcesByRelation(plcLogicalDevice, PARAM_TELEGRAMS, true)
					.stream()
					.map(e -> e.accept(plcTelegramToJson(simple)))
					.collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
		}

		return ResponseUtil.toResponse(DATA, dataJ);
	}
}
