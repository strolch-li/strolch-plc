package li.strolch.plc.core.test;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.PlcHandler;
import li.strolch.plc.core.PlcService;
import li.strolch.privilege.model.PrivilegeContext;

public class TogglePlcService extends PlcService {

	public static final String TOGGLER = "Toggler";
	public static final String ON = "On";
	public static final String OFF = "Off";

	private boolean on;
	private ScheduledFuture<?> toggler;

	public TogglePlcService(PlcHandler plcHandler) {
		super(plcHandler);
	}

	@Override
	public void start(StrolchTransaction tx) {
		this.on = getAddressState(tx, TOGGLER, ON);
		this.toggler = this.scheduleAtFixedRate(this::toggle, 10, 10, TimeUnit.SECONDS);
	}

	@Override
	public void stop() {
		if (this.toggler != null)
			this.toggler.cancel(true);
		this.toggler = null;
	}

	private void toggle(PrivilegeContext ctx) {
		if (this.on) {
			logger.info("Toggling Toggle to off!");
			send(TOGGLER, OFF);
			this.on = false;
		} else {
			logger.info("Toggling Toggle to on!");
			send(TOGGLER, ON);
			this.on = true;
		}
	}

	@Override
	protected void handleFailedAsync(Exception e) {
		if (this.toggler != null)
			this.toggler.cancel(true);

		logger.error("Execution of Toggler failed", e);
	}
}
