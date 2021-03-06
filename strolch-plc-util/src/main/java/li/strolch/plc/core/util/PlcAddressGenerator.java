package li.strolch.plc.core.util;

import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;
import static li.strolch.model.xml.StrolchXmlHelper.parseToMap;
import static li.strolch.plc.model.PlcConstants.*;
import static li.strolch.utils.helper.StringHelper.isEmpty;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import li.strolch.model.Resource;
import li.strolch.model.StrolchRootElement;
import li.strolch.model.StrolchValueType;
import li.strolch.model.parameter.BooleanParameter;
import li.strolch.model.parameter.IntegerParameter;
import li.strolch.model.parameter.Parameter;
import li.strolch.model.parameter.StringParameter;
import li.strolch.model.xml.StrolchXmlHelper;
import li.strolch.utils.collections.MapOfLists;
import li.strolch.utils.helper.StringHelper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlcAddressGenerator {

	private static final Logger logger = LoggerFactory.getLogger(PlcAddressGenerator.class);

	public static void main(String[] args) throws IOException {

		if (args.length != 3)
			throw new IllegalStateException("Usage: java " + PlcAddressGenerator.class.getName()
					+ " <templates.xml> <import.csv> <export.csv>");

		File templatesF = new File(args[0]);
		File importFile = new File(args[1]);
		File exportFile = new File(args[2]);

		if (!templatesF.isFile() || !templatesF.canRead())
			throw new IllegalArgumentException("Templates file is not readable at " + templatesF.getAbsolutePath());
		if (!importFile.isFile() || !importFile.canRead())
			throw new IllegalArgumentException("Import file is not readable at " + importFile.getAbsolutePath());
		if (exportFile.isDirectory())
			throw new IllegalArgumentException("Export file is invalid at " + exportFile.getAbsolutePath());
		if (!exportFile.getParentFile().isDirectory() || (exportFile.exists() && !exportFile.canWrite()) || (
				!exportFile.exists() && !exportFile.getParentFile().canWrite()))
			throw new IllegalArgumentException(
					"Export file is writeable or creatable at " + exportFile.getAbsolutePath());

		new PlcAddressGenerator().generate(templatesF, importFile, exportFile);
	}

	private void add(Map<String, Resource> exportList, Resource element) {
		Resource replaced = exportList.put(element.getId(), element);
		if (replaced != null) {
			throw new IllegalStateException("A " + element.getType() + " " + element.getId() + ". Addresses: " + element
					.getParameter(PARAM_ADDRESS, true).getValue() + " and replaced with " + replaced
					.getParameter(PARAM_ADDRESS, true).getValue());
		}
	}

	public void generate(File templatesFile, File importFile, File exportFile) throws IOException {

		Map<String, StrolchRootElement> elementsById = parseToMap(templatesFile);
		Resource logicalDeviceT = elementsById.get(TYPE_PLC_LOGICAL_DEVICE).asResource();
		Resource addressT = elementsById.get(TYPE_PLC_ADDRESS).asResource();
		Resource telegramT = elementsById.get(TYPE_PLC_TELEGRAM).asResource();

		logicalDeviceT.setType(logicalDeviceT.getId());
		addressT.setType(addressT.getId());
		telegramT.setType(telegramT.getId());

		Map<String, Resource> exportList = new LinkedHashMap<>();

		CSVFormat csvFormat = CSVFormat.DEFAULT.withFirstRecordAsHeader();
		try (InputStream in = new FileInputStream(importFile);
				CSVParser parser = CSVParser.parse(in, UTF_8, csvFormat)) {

			int groupNr = 0;
			int groupIndex = 10;
			int addressIndex = 10;
			int telegramIndex = 10;

			Resource logicalDevice = null;

			for (CSVRecord record : parser) {

				String type = record.get("Type");
				if (type.isEmpty()) {
					logger.info("Ignoring empty type for " + record);
					continue;
				}

				String description = record.get("Description").trim();
				String resource = record.get("Resource").trim();
				String action1 = record.get("Action1").trim();
				String connection = record.get("Connection").trim();

				String key = resource + "-" + action1;
				String keyName = resource + " - " + action1;

				if (type.equals("Group")) {
					groupNr++;
					addressIndex = 10;
					telegramIndex = 10;

					String deviceId = record.get("DeviceId").trim();
					if (isEmpty(deviceId))
						throw new IllegalStateException("No device for new group: " + record);

					logicalDevice = logicalDeviceT.getClone();
					logicalDevice.setId("D_" + deviceId);
					logicalDevice.setName(deviceId);

					String groupNrS = StringHelper.normalizeLength(Integer.toString(groupNr), 2, true, '0');
					logicalDevice.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					logicalDevice.getParameter(PARAM_GROUP, true).setValue(groupNrS + " " + description);
					logicalDevice.getParameter(PARAM_INDEX, true).setValue(groupIndex);

					add(exportList, logicalDevice);
					groupIndex += 10;
					logger.info("Added PlcLogicalDevice " + logicalDevice.getId());

				} else if (type.equals("Input")) {

					if (isEmpty(resource))
						throw new IllegalStateException("resource missing for: " + record);
					if (isEmpty(action1))
						throw new IllegalStateException("action1 missing for: " + record);
					if (isEmpty(connection))
						throw new IllegalStateException("connection missing for: " + record);
					if (logicalDevice == null)
						throw new IllegalStateException(
								"No PlcLogicalDevice exists for address with keys " + resource + "-" + action1);

					String subType = record.get("SubType").trim();
					if (isEmpty(connection))
						throw new IllegalStateException("SubType missing for: " + record);

					String address = evaluateAddress(subType, record, connection);

					Resource addressR = addressT.getClone();
					addressR.setId("A_" + key);
					addressR.setName(keyName);

					addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					addressR.getParameter(PARAM_ADDRESS, true).setValue(address);
					addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
					addressR.getParameter(PARAM_ACTION, true).setValue(action1);

					if (record.isSet("Inverted") && record.get("Inverted").equalsIgnoreCase("true"))
						addressR.getParameter(PARAM_INVERTED, true).setValue(true);

					addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
					addressIndex += 10;

					BooleanParameter valueP = new BooleanParameter(PARAM_VALUE, "Value", false);
					valueP.setIndex(100);
					addressR.addParameter(valueP);

					add(exportList, addressR);
					logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
					logger.info(
							"Added Boolean PlcAddress " + addressR.getId() + " " + addressR.getName() + " for address "
									+ address);

				} else if (type.equals("Output")) {

					if (isEmpty(resource))
						throw new IllegalStateException("resource missing for: " + record);
					if (isEmpty(action1))
						throw new IllegalStateException("action1 missing for: " + record);
					if (isEmpty(connection))
						throw new IllegalStateException("connection missing for: " + record);
					if (logicalDevice == null)
						throw new IllegalStateException(
								"No PlcLogicalDevice exists for address with keys " + resource + "-" + action1);

					String subType = record.get("SubType").trim();
					if (isEmpty(connection))
						throw new IllegalStateException("SubType missing for: " + record);

					String address = evaluateAddress(subType, record, connection);

					Resource telegramR;
					BooleanParameter valueP;

					// action1 value
					telegramR = telegramT.getClone();
					telegramR.setId("T_" + key);
					telegramR.setName(keyName);

					telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					telegramR.getParameter(PARAM_ADDRESS, true).setValue(address);
					telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
					telegramR.getParameter(PARAM_ACTION, true).setValue(action1);

					telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
					telegramIndex += 10;

					valueP = new BooleanParameter(PARAM_VALUE, "Value", true);
					valueP.setIndex(100);
					telegramR.addParameter(valueP);

					add(exportList, telegramR);
					logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
					logger.info("Added Boolean PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
							+ " for address " + address);

					// action2 value
					if (record.isSet("Action2") && isNotEmpty(record.get("Action2").trim())) {
						String action2 = record.get("Action2").trim();

						telegramR = telegramT.getClone();
						telegramR.setId("T_" + resource + "-" + action2);
						telegramR.setName(resource + " - " + action2);

						telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						telegramR.getParameter(PARAM_ADDRESS, true).setValue(address);
						telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						telegramR.getParameter(PARAM_ACTION, true).setValue(action2);

						telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
						telegramIndex += 10;

						valueP = new BooleanParameter(PARAM_VALUE, "Value", false);
						valueP.setIndex(100);
						telegramR.addParameter(valueP);

						add(exportList, telegramR);
						logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
						logger.info("Added Boolean PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
								+ " for address " + address);
					}

					// validate address exists for this address
					if (exportList.values().stream().filter(e -> e.getType().equals(TYPE_PLC_ADDRESS))
							.noneMatch(e -> e.getParameter(PARAM_ADDRESS).getValue().equals(address))) {

						Resource addressR = addressT.getClone();
						addressR.setId("A_" + key);
						addressR.setName(keyName);

						addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						addressR.getParameter(PARAM_ADDRESS, true).setValue(address);
						addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						addressR.getParameter(PARAM_ACTION, true).setValue(action1);

						addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
						addressIndex += 10;

						valueP = new BooleanParameter(PARAM_VALUE, "Value", false);
						valueP.setIndex(100);
						addressR.addParameter(valueP);

						add(exportList, addressR);
						logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
						logger.info("Added missing Boolean PlcAddress " + addressR.getId() + " " + addressR.getName()
								+ " for address " + address);
					}

				} else if (type.equals("Virtual")) {

					if (isEmpty(resource))
						throw new IllegalStateException("resource missing for: " + record);
					if (isEmpty(action1))
						throw new IllegalStateException("action1 missing for: " + record);
					if (isEmpty(connection))
						throw new IllegalStateException("connection missing for: " + record);
					if (logicalDevice == null)
						throw new IllegalStateException(
								"No PlcLogicalDevice exists for address with keys " + resource + "-" + action1);

					String subType = record.get("SubType").trim();
					if (isEmpty(connection))
						throw new IllegalStateException("SubType missing for: " + record);

					Resource telegramR;

					String value = record.isSet("Value") ? record.get("Value") : null;

					if (subType.equals("Boolean")) {

						// address for virtual boolean
						Resource addressR = addressT.getClone();
						addressR.setId("A_" + key);
						addressR.setName(keyName);

						addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						addressR.getParameter(PARAM_ADDRESS, true).setValue(connection);
						addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						addressR.getParameter(PARAM_ACTION, true).setValue(action1);

						addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
						addressIndex += 10;

						Parameter<?> valueP = new BooleanParameter(PARAM_VALUE, "Value", false);
						valueP.setIndex(100);
						addressR.addParameter(valueP);

						add(exportList, addressR);
						logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
						logger.info("Added Virtual Boolean PlcAddress " + addressR.getId() + " " + addressR.getName()
								+ " for connection " + connection);

						// telegram for action1
						telegramR = telegramT.getClone();
						telegramR.setId("T_" + key);
						telegramR.setName(keyName);

						telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						telegramR.getParameter(PARAM_ADDRESS, true).setValue(connection);
						telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						telegramR.getParameter(PARAM_ACTION, true).setValue(action1);

						telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
						telegramIndex += 10;

						valueP = new BooleanParameter(PARAM_VALUE, "Value", true);
						valueP.setIndex(100);
						telegramR.addParameter(valueP);

						add(exportList, telegramR);
						logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
						logger.info("Added Virtual Boolean PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
								+ " for connection " + connection);

						// telegram for action2
						if (record.isSet("Action2") && isNotEmpty(record.get("Action2").trim())) {
							String action2 = record.get("Action2").trim();
							key = resource + "-" + action2;
							keyName = resource + " - " + action2;
							telegramR = telegramT.getClone();
							telegramR.setId("T_" + key);
							telegramR.setName(keyName);

							telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
							telegramR.getParameter(PARAM_ADDRESS, true).setValue(connection);
							telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
							telegramR.getParameter(PARAM_ACTION, true).setValue(action2);

							telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
							telegramIndex += 10;

							valueP = new BooleanParameter(PARAM_VALUE, "Value", false);
							valueP.setIndex(100);
							telegramR.addParameter(valueP);

							add(exportList, telegramR);
							logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true)
									.addValueIfNotContains(telegramR.getId());
							logger.info(
									"Added Virtual Boolean PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
											+ " for connection " + connection);
						}

					} else if (subType.equals("String")) {

						// address for virtual string
						Resource addressR = addressT.getClone();
						addressR.setId("A_" + key);
						addressR.setName(keyName);

						addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						addressR.getParameter(PARAM_ADDRESS, true).setValue(connection);
						addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						addressR.getParameter(PARAM_ACTION, true).setValue(action1);

						addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
						addressIndex += 10;

						Parameter<?> valueP = new StringParameter(PARAM_VALUE, "Value", value == null ? "" : value);
						valueP.setIndex(100);
						addressR.addParameter(valueP);

						add(exportList, addressR);
						logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
						logger.info("Added Virtual String PlcAddress " + addressR.getId() + " " + addressR.getName()
								+ " for connection " + connection);

						// telegram for action1
						telegramR = telegramT.getClone();
						telegramR.setId("T_" + key);
						telegramR.setName(keyName);

						telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						telegramR.getParameter(PARAM_ADDRESS, true).setValue(connection);
						telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						telegramR.getParameter(PARAM_ACTION, true).setValue(action1);

						telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
						telegramIndex += 10;

						valueP = new StringParameter(PARAM_VALUE, "Value", "");
						valueP.setIndex(100);
						telegramR.addParameter(valueP);

						add(exportList, telegramR);
						logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
						logger.info("Added Virtual String PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
								+ " for connection " + connection);

					} else if (subType.equals("Integer")) {

						// address for virtual integer
						Resource addressR = addressT.getClone();
						addressR.setId("A_" + key);
						addressR.setName(keyName);

						addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						addressR.getParameter(PARAM_ADDRESS, true).setValue(connection);
						addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						addressR.getParameter(PARAM_ACTION, true).setValue(action1);

						addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
						addressIndex += 10;

						Parameter<?> valueP = new IntegerParameter(PARAM_VALUE, "Value",
								value == null ? 0 : parseInt(record.get("Value")));
						valueP.setIndex(100);
						addressR.addParameter(valueP);

						add(exportList, addressR);
						logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
						logger.info("Added Virtual Integer PlcAddress " + addressR.getId() + " " + addressR.getName()
								+ " for connection " + connection);

						// telegram for action1
						telegramR = telegramT.getClone();
						telegramR.setId("T_" + key);
						telegramR.setName(keyName);

						telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
						telegramR.getParameter(PARAM_ADDRESS, true).setValue(connection);
						telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
						telegramR.getParameter(PARAM_ACTION, true).setValue(action1);

						telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
						telegramIndex += 10;

						valueP = new IntegerParameter(PARAM_VALUE, "Value",
								value == null ? 0 : parseInt(record.get("Value")));
						valueP.setIndex(100);
						telegramR.addParameter(valueP);

						add(exportList, telegramR);
						logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
						logger.info("Added Virtual Integer PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
								+ " for connection " + connection);

					} else {
						throw new IllegalArgumentException(
								"Unhandled virtual connection " + connection + " for " + resource + "-" + action1);
					}

				} else if (type.equals("DataLogicScanner")) {

					if (isEmpty(resource))
						throw new IllegalStateException("resource missing for: " + record);
					if (logicalDevice == null)
						throw new IllegalStateException(
								"No PlcLogicalDevice exists for address with keys " + resource + "-" + action1);

					Resource addressR;
					Resource telegramR;

					// address for barcode
					key = resource + "-Barcode";
					keyName = resource + " - Barcode";
					addressR = addressT.getClone();
					addressR.setId("A_" + key);
					addressR.setName(keyName);

					addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					addressR.getParameter(PARAM_ADDRESS, true).setValue(connection + ".barcode");
					addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
					addressR.getParameter(PARAM_ACTION, true).setValue("Barcode");

					addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
					addressIndex += 10;

					StringParameter valueP = new StringParameter(PARAM_VALUE, "Value", "");
					valueP.setIndex(100);
					addressR.addParameter(valueP);

					add(exportList, addressR);
					logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
					logger.info("Added DataLogicScanner PlcAddress " + addressR.getId() + " " + addressR.getName()
							+ " for connection " + connection);

					// address for on
					key = resource + "-On";
					keyName = resource + " - On";
					addressR = addressT.getClone();
					addressR.setId("A_" + key);
					addressR.setName(keyName);

					addressR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					addressR.getParameter(PARAM_ADDRESS, true).setValue(connection + ".trigger");
					addressR.getParameter(PARAM_RESOURCE, true).setValue(resource);
					addressR.getParameter(PARAM_ACTION, true).setValue("On");

					addressR.getParameter(PARAM_INDEX, true).setValue(addressIndex);
					addressIndex += 10;

					BooleanParameter booleanValueP = new BooleanParameter(PARAM_VALUE, "Value", false);
					booleanValueP.setIndex(100);
					addressR.addParameter(booleanValueP);

					add(exportList, addressR);
					logicalDevice.getRelationsParam(PARAM_ADDRESSES, true).addValueIfNotContains(addressR.getId());
					logger.info("Added DataLogicScanner PlcAddress " + addressR.getId() + " " + addressR.getName()
							+ " for connection " + connection);

					// telegram for trigger on
					telegramR = telegramT.getClone();
					telegramR.setId("T_" + key);
					telegramR.setName(keyName);

					telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					telegramR.getParameter(PARAM_ADDRESS, true).setValue(connection + ".trigger");
					telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
					telegramR.getParameter(PARAM_ACTION, true).setValue("On");

					telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
					telegramIndex += 10;

					booleanValueP = new BooleanParameter(PARAM_VALUE, "Value", true);
					booleanValueP.setIndex(100);
					telegramR.addParameter(booleanValueP);

					add(exportList, telegramR);
					logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
					logger.info("Added DataLogicScanner PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
							+ " for connection " + connection);

					// telegram for trigger off
					key = resource + "-Off";
					keyName = resource + " - Off";
					telegramR = telegramT.getClone();
					telegramR.setId("T_" + key);
					telegramR.setName(keyName);

					telegramR.getParameter(PARAM_DESCRIPTION, true).setValue(description);
					telegramR.getParameter(PARAM_ADDRESS, true).setValue(connection + ".trigger");
					telegramR.getParameter(PARAM_RESOURCE, true).setValue(resource);
					telegramR.getParameter(PARAM_ACTION, true).setValue("Off");

					telegramR.getParameter(PARAM_INDEX, true).setValue(telegramIndex);
					telegramIndex += 10;

					booleanValueP = new BooleanParameter(PARAM_VALUE, "Value", false);
					booleanValueP.setIndex(100);
					telegramR.addParameter(booleanValueP);

					add(exportList, telegramR);
					logicalDevice.getRelationsParam(PARAM_TELEGRAMS, true).addValueIfNotContains(telegramR.getId());
					logger.info("Added DataLogicScanner PlcTelegram " + telegramR.getId() + " " + telegramR.getName()
							+ " for connection " + connection);

				} else {
					throw new IllegalStateException("Unhandled type " + type + " for " + record);
				}
			}
		}

		// validate
		boolean valid = true;
		MapOfLists<String, Resource> elementsByAddress = exportList.values().stream()
				.filter(e -> !e.getType().equals(TYPE_PLC_LOGICAL_DEVICE)).collect(MapOfLists::new,
						(m, e) -> m.addElement(e.getParameter(PARAM_ADDRESS, true).getValueAsString(), e),
						MapOfLists::addAll);
		for (String address : elementsByAddress.keySet()) {
			List<Resource> elements = elementsByAddress.getList(address);
			if (elements.size() <= 1)
				continue;

			List<Resource> addresses = elements.stream().filter(e -> e.getType().equals(TYPE_PLC_ADDRESS))
					.collect(Collectors.toList());
			if (addresses.size() > 1) {
				logger.warn("Multiple elements with address " + address);
				for (Resource o : elements) {
					logger.warn("\t" + o.getId() + " " + o.getName());
				}
				valid = false;
			}

			List<Resource> telegrams = elements.stream().filter(e -> e.getType().equals(TYPE_PLC_TELEGRAM))
					.collect(Collectors.toList());
			StrolchValueType valueType = addresses.get(0).getParameter(PARAM_VALUE, true).getValueType();
			if (valueType == StrolchValueType.BOOLEAN) {
				if (telegrams.size() != 2) {
					logger.error("Expected to have 2 telegrams for Boolean address " + address + ", but there are: "
							+ telegrams.size());
					for (Resource telegram : telegrams) {
						logger.error("\t" + telegram.getId() + " " + telegram.getName());
					}
					valid = false;
				} else {
					Resource telegram1 = telegrams.get(0);
					Resource telegram2 = telegrams.get(1);
					Boolean value1 = telegram1.getParameter(PARAM_VALUE, true).getValue();
					Boolean value2 = telegram2.getParameter(PARAM_VALUE, true).getValue();
					if (!value1 || value2) {
						logger.error("Unexpected values for telegrams: ");
						logger.error("\t" + value1 + " for " + telegram1.getId() + " " + telegram1.getName());
						logger.error("\t" + value2 + " for " + telegram2.getId() + " " + telegram2.getName());
						valid = false;
					}
				}
			} else {
				logger.warn("No validation available for value type " + valueType + " and address " + address);
				for (Resource element : elements) {
					logger.warn("\t" + element.getId() + " " + element.getName());
				}
			}
		}

		if (valid)
			logger.info("Validation ok of " + elementsByAddress.size() + " addresses");
		else
			logger.error("At least one address is invalid!");

		StrolchXmlHelper.writeToFile(exportFile, exportList.values());
		logger.info("Wrote " + exportList.size() + " elements to " + exportFile);
	}

	private String evaluateAddress(String subType, CSVRecord record, String connection) {

		String pin = record.get("Pin").trim();
		if (isEmpty(pin))
			throw new IllegalStateException("Pin missing for: " + record);

		String address;
		if (subType.equals("Pin")) {
			int io = parseInt(pin);
			address = connection + "." + io;
			return address;
		}

		if (subType.equals("DevPin0") || subType.equals("DevPin")) {

			String device = record.get("Device").trim();
			if (isEmpty(device))
				throw new IllegalStateException("Device missing for: " + record);

			int card = parseInt(device);
			int io = parseInt(pin);

			if (subType.equals("DevPin0")) {
				int dev = parseInt(connection.substring(connection.length() - 2));
				card -= dev;
				io -= 1;
			}

			address = connection + "." + card + "." + io;

			return address;
		}

		throw new UnsupportedOperationException("Unhandled subType " + subType);
	}
}
