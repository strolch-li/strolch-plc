package li.strolch.plc.core.hw.gpio;

import com.pi4j.concurrent.SingleThreadGpioExecutorServiceFactory;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.RaspiPinNumberingScheme;

public class PlcGpioController {

	private static GpioController controller;

	public static GpioController getInstance() {
		if (controller != null)
			return controller;

		GpioFactory.setExecutorServiceFactory(new SingleThreadGpioExecutorServiceFactory());
		GpioFactory.setDefaultProvider(new RaspiGpioProvider(RaspiPinNumberingScheme.BROADCOM_PIN_NUMBERING));
		controller = GpioFactory.getInstance();

		return controller;
	}
}
