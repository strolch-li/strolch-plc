package li.strolch.plc.gw.server;

import static li.strolch.agent.api.AgentVersion.*;
import static li.strolch.agent.api.ComponentVersion.COMPONENT_VERSION;
import static li.strolch.model.Tags.Json.AGENT_VERSION;
import static li.strolch.model.Tags.Json.APP_VERSION;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.utils.helper.StringHelper.DASH;
import static li.strolch.utils.helper.StringHelper.isEmpty;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.model.Tags;
import li.strolch.model.json.ResourceSystemStateFromJson;
import li.strolch.model.parameter.StringListParameter;
import li.strolch.model.parameter.StringParameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.ConnectionState;
import li.strolch.privilege.model.Certificate;
import li.strolch.runtime.privilege.PrivilegedRunnable;
import li.strolch.utils.DataUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlcStateHandler {

	private static final Logger logger = LoggerFactory.getLogger(PlcStateHandler.class);

	private final ComponentContainer container;

	public PlcStateHandler(ComponentContainer container) {
		this.container = container;
	}

	protected void runAsAgent(PrivilegedRunnable runnable) throws Exception {
		this.container.getPrivilegeHandler().runAsAgent(runnable);
	}

	protected StrolchTransaction openTx(Certificate cert) {
		return this.container.getRealm(cert).openTx(cert, getClass(), false);
	}

	public void handleStillConnected(PlcGwServerHandler.PlcSession plcSession) {
		try {
			runAsAgent(ctx -> {
				try (StrolchTransaction tx = openTx(ctx.getCertificate())) {

					// get the gateway and set the state
					Resource plc = tx.getResourceBy(TYPE_PLC, plcSession.plcId, true);
					StringParameter stateP = plc.getParameter(PARAM_CONNECTION_STATE, true);

					if (!stateP.getValue().equals(ConnectionState.Connected.name())) {
						stateP.setValue(ConnectionState.Connected.name());
						StringParameter stateMsgP = plc.getParameter(PARAM_CONNECTION_STATE_MSG, true);
						stateMsgP.setValue("");
						tx.update(plc);
						tx.commitOnClose();
					}
				}
			});
		} catch (Exception e) {
			logger.error("Failed to handle still connected notification!", e);
		}
	}

	public void handlePlcState(PlcGwServerHandler.PlcSession plcSession, ConnectionState connectionState,
			String connectionStateMsg, JsonObject stateJ) {
		try {
			runAsAgent(ctx -> {
				try (StrolchTransaction tx = openTx(plcSession.certificate)) {

					// get the gateway and set the state
					Resource plc = tx.getResourceBy(TYPE_PLC, plcSession.plcId, true);
					StringParameter stateP = plc.getParameter(PARAM_CONNECTION_STATE, true);
					stateP.setValue(connectionState.name());
					StringParameter stateMsgP = plc.getParameter(PARAM_CONNECTION_STATE_MSG, true);
					stateMsgP.setValue(connectionStateMsg);
					tx.update(plc);

					if (stateJ != null) {
						saveGatewayIpAddresses(tx, plc, stateJ.getAsJsonArray(PARAM_IP_ADDRESSES));
						saveGatewayVersion(tx, plc, stateJ.getAsJsonObject(PARAM_VERSIONS));
						setSystemState(stateJ.getAsJsonObject(PARAM_SYSTEM_STATE), plc);
					}

					logger.info("Updated connection state for PLC " + plc.getId() + " to " + connectionState + (isEmpty(
							connectionStateMsg) ? "" : ": " + connectionStateMsg));
					tx.commitOnClose();
				}
			});
		} catch (Exception e) {
			logger.error("Failed to handle gateway connection state notification!", e);
		}
	}

	private void saveGatewayIpAddresses(StrolchTransaction tx, Resource plc, JsonArray ipAddresses) {
		if (ipAddresses.size() == 0)
			return;

		// update local IPs
		StringListParameter localIpP = plc.getParameter(PARAM_LOCAL_IP, true);
		List<String> ipAddressesS = StreamSupport.stream(ipAddresses.spliterator(), false).map(e -> {
			JsonObject jsonObject = e.getAsJsonObject();
			String ip = jsonObject.getAsJsonPrimitive(PARAM_IP_ADDRESS).getAsString();
			String hostname = jsonObject.getAsJsonPrimitive(PARAM_HOST_NAME).getAsString();
			String mac = jsonObject.getAsJsonPrimitive(PARAM_MAC_ADDRESS).getAsString();
			return ip + " | " + hostname + " | " + mac;
		}).collect(Collectors.toList());

		if (!localIpP.getValue().equals(ipAddressesS)) {
			localIpP.setValue(ipAddressesS);
			tx.update(plc);
		}
	}

	private void saveGatewayVersion(StrolchTransaction tx, Resource plc, JsonObject versions) {
		if (versions == null || versions.isJsonNull())
			return;

		if (versions.has(AGENT_VERSION)) {
			JsonObject agentVersion = versions.get(AGENT_VERSION).getAsJsonObject();
			ParameterBag bag = updateVersionParams(plc, AGENT_VERSION, "Agent Version", agentVersion);
			setOrAdd(bag, versions, AGENT_NAME, "Agent Name");
			tx.update(plc);
		}

		if (versions.has(APP_VERSION)) {
			JsonObject appVersion = versions.get(APP_VERSION).getAsJsonObject();
			updateVersionParams(plc, APP_VERSION, "App Version", appVersion);
			tx.update(plc);
		}

		if (versions.has(Tags.Json.COMPONENT_VERSIONS)) {
			JsonArray componentVersions = versions.get(Tags.Json.COMPONENT_VERSIONS).getAsJsonArray();
			componentVersions.forEach(e -> {
				JsonObject componentVersion = e.getAsJsonObject();
				String componentName = componentVersion.get(COMPONENT_VERSION).getAsString();
				updateVersionParams(plc, componentName, "Component " + componentName, componentVersion);
				tx.update(plc);
			});
		}
	}

	private ParameterBag updateVersionParams(Resource gateway, String bagKey, String bagName, JsonObject version) {
		ParameterBag bag = gateway.getParameterBag(bagKey);
		if (bag == null) {
			bag = new ParameterBag(bagKey, bagName, Tags.VERSION);
			gateway.addParameterBag(bag);
		}

		setOrAdd(bag, version, BUILD_TIMESTAMP, "Build timestamp");
		setOrAdd(bag, version, SCM_BRANCH, "SCM Branch");
		setOrAdd(bag, version, SCM_REVISION, "SCM Revision");
		StringParameter versionP = setOrAdd(bag, version, ARTIFACT_VERSION, "Artifact Version");
		versionP.setInterpretation("Version");
		versionP.setUom("Version");
		setOrAdd(bag, version, ARTIFACT_ID, "Artifact ID");
		setOrAdd(bag, version, GROUP_ID, "Group ID");

		return bag;
	}

	private StringParameter setOrAdd(ParameterBag bag, JsonObject version, String paramKey, String paramName) {
		String value = version.has(paramKey) ? version.get(paramKey).getAsString() : DASH;
		StringParameter param = bag.getParameter(paramKey);
		if (param == null) {
			param = new StringParameter(paramKey, paramName, value);
			bag.addParameter(param);
		} else {
			param.setValue(value);
		}

		return param;
	}

	private void setSystemState(JsonObject systemState, Resource gateway) {
		if (systemState == null)
			return;

		new ResourceSystemStateFromJson().compactStates() //

				// os
				.withSystemLoadAverageState() //

				// memory
				.withMemoryRounding(DataUnit.MegaBytes) //
				.withFreePhysicalMemorySizeState() //

				// storage
				.withStorageSpaceRounding(DataUnit.GigaBytes) //
				.withFreeSpaceState() //

				.fillElement(systemState, gateway);
	}
}
