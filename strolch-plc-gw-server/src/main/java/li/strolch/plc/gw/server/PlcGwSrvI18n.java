package li.strolch.plc.gw.server;

import java.util.Locale;
import java.util.ResourceBundle;

public class PlcGwSrvI18n {

	private static final String BUNDLE = "strolch-plc-gw-server";

	public static final ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE);

	public static ResourceBundle getBundle(Locale locale) {
		return ResourceBundle.getBundle(BUNDLE, locale);
	}

	public static String i18n(Locale locale, String key) {
		ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale);
		if (bundle.containsKey(key))
			return bundle.getString(key);
		return key;
	}

}
