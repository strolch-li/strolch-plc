package li.strolch.plc.core;

import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.model.StrolchModelConstants.BAG_PARAMETERS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import li.strolch.model.parameter.Parameter;
import li.strolch.plc.core.hw.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressType;
import li.strolch.search.ResourceSearch;
import li.strolch.utils.collections.MapOfMaps;
import li.strolch.utils.helper.ClassHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PlcConfigurator {

	private static final Logger logger = LoggerFactory.getLogger(PlcConfigurator.class);

	static Plc configurePlc(StrolchTransaction tx, String plcClassName,
			MapOfMaps<String, String, PlcAddress> plcAddresses, MapOfMaps<String, String, PlcAddress> plcTelegrams,
			Map<PlcAddress, String> addressesToResourceId) throws Exception {

		// instantiate Plc
		logger.info("Configuring PLC " + plcClassName + "...");
		Plc plc = ClassHelper.instantiateClass(plcClassName);

		// instantiate all PlcConnections
		List<Resource> connections = new ResourceSearch().types(TYPE_PLC_CONNECTION).search(tx).toList();
		for (Resource c : connections) {
			configureConnection(plc, c);
		}

		Map<String, PlcAddress> plcAddressesByHwAddress = new HashMap<>();

		// query PlcLogicalDevices
		List<Resource> logicalDevices = new ResourceSearch().types(TYPE_PLC_LOGICAL_DEVICE).search(tx).toList();

		// first all addresses
		for (Resource resource : logicalDevices) {
			logger.info("Configuring PlcAddresses for PlcLogicalDevice " + resource.getId() + "...");
			List<Resource> addresses = tx.getResourcesByRelation(resource, PARAM_ADDRESSES, true);
			if (addresses.isEmpty()) {
				logger.warn("\tNo PlcAddresses for " + resource.getId());
			} else {
				for (Resource addressRes : addresses) {
					buildPlcAddress(plc, plcAddresses, addressesToResourceId, plcAddressesByHwAddress, addressRes);
				}
			}
		}

		// now telegrams
		for (Resource logicalDevice : logicalDevices) {
			logger.info("Configuring PlcTelegrams for PlcLogicalDevice " + logicalDevice.getId() + "...");
			List<Resource> telegrams = tx.getResourcesByRelation(logicalDevice, PARAM_TELEGRAMS, true);
			if (telegrams.isEmpty()) {
				logger.warn("\tNo PlcTelegrams for " + logicalDevice.getId());
			} else {
				for (Resource telegramRes : telegrams) {
					buildTelegramPlcAddress(plcAddresses, plcTelegrams, addressesToResourceId, plcAddressesByHwAddress,
							telegramRes);
				}
			}
		}

		return plc;
	}

	private static void configureConnection(Plc plc, Resource connection) throws Exception {
		String className = connection.getParameter(BAG_PARAMETERS, PARAM_CLASS_NAME, true).getValue();
		logger.info("Configuring PLC Connection " + className + "...");
		PlcConnection plcConnection = ClassHelper
				.instantiateClass(className, new Class<?>[] { Plc.class, String.class },
						new Object[] { plc, connection.getId() });
		plcConnection.initialize(connection.getParameterBag(BAG_PARAMETERS, true).toObjectMap());
		plc.addConnection(plcConnection);
	}

	private static void buildPlcAddress(Plc plc, MapOfMaps<String, String, PlcAddress> plcAddresses,
			Map<PlcAddress, String> addressesToResourceId, Map<String, PlcAddress> plcAddressesByHwAddress,
			Resource addressRes) {

		String address = addressRes.getParameter(PARAM_ADDRESS, true).getValue();
		String resource = addressRes.getParameter(PARAM_RESOURCE, true).getValue();
		String action = addressRes.getParameter(PARAM_ACTION, true).getValue();
		Parameter<?> valueP = addressRes.getParameter(PARAM_VALUE, true);
		boolean inverted =
				addressRes.hasParameter(PARAM_INVERTED) && ((boolean) addressRes.getParameter(PARAM_INVERTED, true)
						.getValue());

		PlcAddress plcAddress = new PlcAddress(PlcAddressType.Notification, resource, action, address,
				valueP.getValueType(), valueP.getValue(), inverted);
		plc.registerNotificationMapping(plcAddress);

		PlcAddress replaced = plcAddresses.addElement(resource, action, plcAddress);
		if (replaced != null)
			throw new IllegalStateException(
					"Duplicate " + resource + "-" + action + ". Replaced: " + replaced + " with " + plcAddress);

		addressesToResourceId.put(plcAddress, addressRes.getId());
		plcAddressesByHwAddress.put(plcAddress.address, plcAddress);
	}

	private static void buildTelegramPlcAddress(MapOfMaps<String, String, PlcAddress> plcAddresses,
			MapOfMaps<String, String, PlcAddress> plcTelegrams, Map<PlcAddress, String> addressesToResourceId,
			Map<String, PlcAddress> plcAddressesByHwAddress, Resource telegramRes) {

		String address = telegramRes.getParameter(PARAM_ADDRESS, true).getValue();
		String resource = telegramRes.getParameter(PARAM_RESOURCE, true).getValue();
		String action = telegramRes.getParameter(PARAM_ACTION, true).getValue();
		Parameter<?> valueP = telegramRes.getParameter(PARAM_VALUE, true);

		PlcAddress existingAddress = plcAddressesByHwAddress.get(address);
		if (existingAddress == null)
			throw new IllegalStateException(
					telegramRes.getLocator() + " is referencing non-existing address " + address);

		if (valueP.getValueType() != existingAddress.valueType) {
			throw new IllegalStateException(
					telegramRes.getLocator() + " has valueType " + valueP.getValueType() + " but address "
							+ existingAddress.address + " has type " + existingAddress.valueType);
		}

		PlcAddress telegramAddress = new PlcAddress(PlcAddressType.Telegram, resource, action, address,
				valueP.getValueType(), valueP.getValue(), false);
		logger.info("Adding " + telegramAddress + "...");

		PlcAddress replaced = plcTelegrams.addElement(resource, action, telegramAddress);
		if (replaced != null)
			throw new IllegalStateException(
					"Duplicate " + resource + "-" + action + ". Replaced: " + replaced + " with " + telegramAddress);
		if (!plcAddresses.containsElement(resource, action))
			plcAddresses.addElement(resource, action, telegramAddress);

		PlcAddress plcAddress = plcAddresses.getElement(existingAddress.resource, existingAddress.action);
		if (plcAddress == null)
			throw new IllegalStateException(
					"PlcAddress for " + resource + "-" + action + " does not exist, so can not connect PlcTelegram "
							+ telegramAddress);
		String addressId = addressesToResourceId.get(plcAddress);
		if (addressId == null)
			throw new IllegalStateException(
					"PlcAddress mapping ID for " + resource + "-" + action + " does not exist!");
		addressesToResourceId.put(telegramAddress, addressId);
	}
}
