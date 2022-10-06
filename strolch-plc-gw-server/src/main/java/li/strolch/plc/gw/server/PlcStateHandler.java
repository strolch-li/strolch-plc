package li.strolch.plc.gw.server;

import static li.strolch.model.Resource.locatorFor;
import static li.strolch.model.Tags.Json.*;
import static li.strolch.model.builder.BuilderHelper.buildParamName;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.utils.helper.StringHelper.DASH;
import static li.strolch.utils.helper.StringHelper.isEmpty;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.execution.ExecutionHandler;
import li.strolch.handler.operationslog.OperationsLog;
import li.strolch.model.Locator;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.model.Tags;
import li.strolch.model.builder.ResourceBuilder;
import li.strolch.model.json.ResourceSystemStateFromJson;
import li.strolch.model.log.LogMessage;
import li.strolch.model.log.LogMessageState;
import li.strolch.model.log.LogSeverity;
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

	private final PlcGwServerHandler gwServerHandler;
	private final ComponentContainer container;

	public PlcStateHandler(PlcGwServerHandler gwServerHandler) {
		this.gwServerHandler = gwServerHandler;
		this.container = gwServerHandler.getContainer();
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
					Resource plc = tx.getResourceBy(TYPE_PLC, plcSession.plcId, false);
					if (plc == null) {
						plc = buildNewPlc(plcSession, tx);
						tx.add(plc);
					}

					StringParameter stateP = plc.getStringP(PARAM_CONNECTION_STATE);
					if (!stateP.getValue().equals(ConnectionState.Connected.name())) {
						stateP.setValue(ConnectionState.Connected.name());
						plc.getStringP(PARAM_CONNECTION_STATE_MSG).clear();
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
				String realm;
				ConnectionState existingState;
				try (StrolchTransaction tx = openTx(plcSession.certificate)) {
					realm = tx.getRealmName();

					// get the gateway and set the state
					Resource plc = tx.getResourceBy(TYPE_PLC, plcSession.plcId, false);
					if (plc == null) {
						plc = buildNewPlc(plcSession, tx);
						tx.add(plc);
					}

					StringParameter stateP = plc.getStringP(PARAM_CONNECTION_STATE);
					existingState = ConnectionState.valueOf(stateP.getValue());
					if (existingState != connectionState) {
						stateP.setValue(connectionState.name());
						plc.setString(PARAM_CONNECTION_STATE_MSG, connectionStateMsg);
						tx.update(plc);

						logger.info(
								"Updated connection state for PLC " + plc.getId() + " to " + connectionState + (isEmpty(
										connectionStateMsg) ? "" : ": " + connectionStateMsg));
					}

					if (stateJ != null) {
						saveGatewayIpAddresses(tx, plc, stateJ.getAsJsonArray(PARAM_IP_ADDRESSES));
						saveGatewayVersion(tx, plc, stateJ.getAsJsonObject(PARAM_VERSIONS));
						setSystemState(stateJ.getAsJsonObject(PARAM_SYSTEM_STATE), plc);
					}

					tx.commitOnClose();
				}

				if (existingState != connectionState && this.container.hasComponent(OperationsLog.class)) {
					OperationsLog operationsLog = this.container.getComponent(OperationsLog.class);
					Locator msgLocator = locatorFor(TYPE_PLC, plcSession.plcId).append(
							ConnectionState.class.getSimpleName());
					operationsLog.updateState(realm, msgLocator, LogMessageState.Inactive);
					if (connectionState == ConnectionState.Connected) {
						operationsLog.addMessage(new LogMessage(realm, plcSession.plcId, msgLocator, LogSeverity.Info,
								LogMessageState.Information, PlcGwSrvI18n.bundle, "execution.plc.connected").value(
								"plc", plcSession.plcId));
					} else {
						operationsLog.addMessage(new LogMessage(realm, plcSession.plcId, msgLocator, LogSeverity.Error,
								LogMessageState.Active, PlcGwSrvI18n.bundle, "execution.plc.connectionLost").value(
								"plc", plcSession.plcId));
					}
				}

				this.gwServerHandler.notifyConnectionState(plcSession.plcId, connectionState);
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

		if (versions.has(COMPONENT_VERSIONS)) {
			JsonArray componentVersions = versions.get(COMPONENT_VERSIONS).getAsJsonArray();
			componentVersions.forEach(e -> {
				JsonObject componentVersion = e.getAsJsonObject();
				String componentName = componentVersion.get(COMPONENT_NAME).getAsString();
				updateVersionParams(plc, componentName, "Component " + componentName, componentVersion);
				tx.update(plc);
			});
		}
	}

	private ParameterBag updateVersionParams(Resource plc, String bagKey, String bagName, JsonObject version) {
		ParameterBag bag = plc.getParameterBag(bagKey);
		if (bag == null) {
			bag = new ParameterBag(bagKey, bagName, Tags.VERSION);
			plc.addParameterBag(bag);
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

	private Resource buildNewPlc(PlcGwServerHandler.PlcSession plcSession, StrolchTransaction tx) {
		return new ResourceBuilder(plcSession.plcId, plcSession.plcId, TYPE_PLC) //
				.defaultBag() //

				.string(PARAM_CONNECTION_STATE, buildParamName(PARAM_CONNECTION_STATE))
				.enumeration(ConnectionState.Disconnected)
				.end() //

				.string(PARAM_CONNECTION_STATE_MSG, buildParamName(PARAM_CONNECTION_STATE_MSG))
				.end() //

				.stringList(PARAM_LOCAL_IP, buildParamName(PARAM_LOCAL_IP))
				.end() //

				.endBag() //
				.build();
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
