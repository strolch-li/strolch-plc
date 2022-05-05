package li.strolch.plc.model;

import li.strolch.model.Tags;

public class PlcConstants {

	public static final String TYPE_PLC = "Plc";
	public static final String TYPE_PLC_CONNECTION = "PlcConnection";
	public static final String TYPE_PLC_LOGICAL_DEVICE = "PlcLogicalDevice";
	public static final String TYPE_PLC_ADDRESS = "PlcAddress";
	public static final String TYPE_PLC_TELEGRAM = "PlcTelegram";

	public static final String BAG_NOTIFICATIONS = "notifications";
	public static final String BAG_TELEGRAMS = "telegrams";

	public static final String PARAM_ACTION = "action";
	public static final String PARAM_DESCRIPTION = "description";
	public static final String PARAM_ADDRESS = "address";
	public static final String PARAM_ADDRESS_TYPE = "addressType";
	public static final String PARAM_ADDRESSES = "addresses";
	public static final String PARAM_AUTH_TOKEN = "authToken";
	public static final String PARAM_CLASS_NAME = "className";
	public static final String PARAM_CONNECTION_STATE = "connectionState";
	public static final String PARAM_CONNECTION_STATE_MSG = "connectionStateMsg";
	public static final String PARAM_GROUP = "group";
	public static final String PARAM_HOST_NAME = "hostname";
	public static final String PARAM_INDEX = "index";
	public static final String PARAM_INTERRUPT_PIN_NAME = "interruptPinName";
	public static final String PARAM_INVERTED = "inverted";
	public static final String PARAM_REMOTE = "remote";
	public static final String PARAM_PLC_ID = "plcId";
	public static final String PARAM_IP_ADDRESS = "ipAddress";
	public static final String PARAM_IP_ADDRESSES = "ipAddresses";
	public static final String PARAM_MAC_ADDRESS = "macAddress";
	public static final String PARAM_MESSAGE_TYPE = "messageType";
	public static final String PARAM_PASSWORD = "password";
	public static final String PARAM_SEQUENCE_ID = "sequenceId";
	public static final String PARAM_ENABLED = "enabled";
	public static final String PARAM_WARNING = "warning";
	public static final String PARAM_RESOURCE = "resource";
	public static final String PARAM_STATE = "state";
	public static final String PARAM_STATE_MSG = "stateMsg";
	public static final String PARAM_SYSTEM_STATE = "systemState";
	public static final String PARAM_TELEGRAMS = "telegrams";
	public static final String PARAM_TYPE = "type";
	public static final String PARAM_USERNAME = Tags.Json.USERNAME;
	public static final String PARAM_VALUE = "value";
	public static final String PARAM_VALUE_TYPE = "valueType";
	public static final String PARAM_VERSIONS = "versions";
	public static final String PARAM_VERBOSE = "verbose";
	public static final String PARAM_LOCAL_IP = "localIp";
	public static final String PARAM_MESSAGE = "message";
	public static final String PARAM_LOCATOR = "locator";
	public static final String PARAM_REALM = "realm";
	public static final String PARAM_SIMULATED = "simulated";

	public static final String INTERPRETATION_NOTIFICATION = "Notification";
	public static final String INTERPRETATION_TELEGRAM = "Telegram";

	public static final String MSG_TYPE_AUTHENTICATION = "Authentication";
	public static final String MSG_TYPE_PLC_NOTIFICATION = "PlcNotification";
	public static final String MSG_TYPE_PLC_TELEGRAM = "PlcTelegram";
	public static final String MSG_TYPE_MESSAGE = "Message";
	public static final String MSG_TYPE_DISABLE_MESSAGE = "DisableMessage";
	public static final String MSG_TYPE_STATE_NOTIFICATION = "StateNotification";

	public static final String ROLE_PLC = "PLC";
}
