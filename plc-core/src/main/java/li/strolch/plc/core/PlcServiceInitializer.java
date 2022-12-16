package li.strolch.plc.core;

import java.util.List;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchComponent;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.PlcServiceState;
import li.strolch.plc.model.PlcState;

public abstract class PlcServiceInitializer extends StrolchComponent {

	private List<PlcService> plcServices;

	public PlcServiceInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	public void start() throws Exception {

		startPlcServices();

		super.start();
	}

	@Override
	public void stop() throws Exception {
		if (this.plcServices != null)
			this.plcServices.forEach(plcService -> {
				try {
					plcService.stop();
				} catch (Exception e) {
					logger.error("Failed to stop PlcService " + plcService.getClass().getName(), e);
				}
				try {
					plcService.unregister();
				} catch (Exception e) {
					logger.error("Failed to unregister PlcService " + plcService.getClass().getName(), e);
				}
			});
		super.stop();
	}

	protected void startPlcServices() {
		PlcHandler plcHandler = getComponent(PlcHandler.class);
		if (plcHandler.getPlcState() != PlcState.Started) {
			logger.error("Can not start PlcServices as PlcState is " + plcHandler.getPlcState());
			return;
		}

		this.plcServices = getPlcServices(plcHandler);
		for (PlcService plcService : this.plcServices) {
			try {
				plcService.register();
			} catch (Exception e) {
				logger.error("Failed to register PlcService " + plcService.getClass().getName(), e);
			}
		}

		try {
			runAsAgent(ctx -> {
				try (StrolchTransaction tx = openTx(ctx.getCertificate(), getClass().getSimpleName(), true)) {
					for (PlcService plcService : this.plcServices) {
						if (plcService.getState() != PlcServiceState.Registered)
							continue;

						try {
							plcService.start(tx);
						} catch (Exception e) {
							logger.error("Failed to register PlcService " + plcService.getClass().getName(), e);
						}
					}
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start PlcServices", e);
		}
	}

	protected abstract List<PlcService> getPlcServices(PlcHandler plcHandler);
}
