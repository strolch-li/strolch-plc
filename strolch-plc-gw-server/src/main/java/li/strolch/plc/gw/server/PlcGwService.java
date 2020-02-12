package li.strolch.plc.gw.server;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.execution.ExecutionHandler;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcNotificationListener;
import li.strolch.plc.model.PlcServiceState;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.privilege.PrivilegedRunnable;
import li.strolch.runtime.privilege.PrivilegedRunnableWithResult;
import li.strolch.utils.dbc.DBC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PlcGwService implements PlcNotificationListener, PlcAddressResponseListener {

	protected static final Logger logger = LoggerFactory.getLogger(PlcGwService.class);
	protected final String plcId;
	protected final ComponentContainer container;
	protected final PlcGwServerHandler plcHandler;

	private PlcServiceState state;

	public PlcGwService(String plcId, ComponentContainer container, PlcGwServerHandler plcHandler) {
		DBC.PRE.assertNotEmpty("plcId must be set!", plcId);
		DBC.PRE.assertNotNull("container must be set!", container);
		DBC.PRE.assertNotNull("plcHandler must be set!", plcHandler);
		this.plcId = plcId;
		this.container = container;
		this.plcHandler = plcHandler;
		this.state = PlcServiceState.Unregistered;
	}

	public PlcServiceState getState() {
		return this.state;
	}

	public void start(StrolchTransaction tx) {
		this.state = PlcServiceState.Started;
	}

	public void stop() {
		this.state = PlcServiceState.Stopped;
	}

	public void register() {
		this.state = PlcServiceState.Registered;
	}

	public void unregister() {
		this.state = PlcServiceState.Unregistered;
	}

	protected void register(PlcAddressKey key) {
		this.plcHandler.register(key, this.plcId, this);
	}

	protected void unregister(PlcAddressKey key) {
		this.plcHandler.unregister(key, this.plcId, this);
	}

	public void sendMessage(PlcAddressKey addressKey, String plcId, boolean value,
			PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(addressKey, plcId, value, this);
	}

	public void sendMessage(PlcAddressKey addressKey, String plcId, int value, PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(addressKey, plcId, value, this);
	}

	public void sendMessage(PlcAddressKey addressKey, String plcId, double value, PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(addressKey, plcId, value, this);
	}

	public void sendMessage(PlcAddressKey addressKey, String plcId, String value, PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(addressKey, plcId, value, this);
	}

	public void sendMessage(PlcAddressKey addressKey, String plcId, PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(addressKey, plcId, this);
	}

	protected StrolchTransaction openTx(PrivilegeContext ctx, boolean readOnly) {
		return this.container.getRealm(ctx.getCertificate()).openTx(ctx.getCertificate(), getClass(), readOnly);
	}

	protected void runAsAgent(PrivilegedRunnable runnable) throws Exception {
		this.container.getPrivilegeHandler().runAsAgent(runnable);
	}

	protected ExecutionHandler getExecutionHandler() {
		return this.container.getComponent(ExecutionHandler.class);
	}

	protected <T> T runAsAgentWithResult(PrivilegedRunnableWithResult<T> runnable) throws Exception {
		return this.container.getPrivilegeHandler().runAsAgentWithResult(runnable);
	}

	protected ScheduledFuture<?> schedule(PrivilegedRunnable runnable, long delay, TimeUnit delayUnit) {
		return this.container.getAgent().getScheduledExecutor(PlcGwService.class.getSimpleName()).schedule(() -> {
			try {
				this.container.getPrivilegeHandler().runAsAgent(runnable);
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, delay, delayUnit);
	}

	protected ScheduledFuture<?> scheduleAtFixedRate(PrivilegedRunnable runnable, long initialDelay, long period,
			TimeUnit delayUnit) {
		return this.container.getAgent().getScheduledExecutor(PlcGwService.class.getSimpleName())
				.scheduleAtFixedRate(() -> {
					try {
						this.container.getPrivilegeHandler().runAsAgent(runnable);
					} catch (Exception e) {
						handleFailedSchedule(e);
					}
				}, initialDelay, period, delayUnit);
	}

	protected ScheduledFuture<?> scheduleWithFixedDelay(PrivilegedRunnable runnable, long initialDelay, long period,
			TimeUnit delayUnit) {
		return this.container.getAgent().getScheduledExecutor(PlcGwService.class.getSimpleName())
				.scheduleWithFixedDelay(() -> {
					try {
						this.container.getPrivilegeHandler().runAsAgent(runnable);
					} catch (Exception e) {
						handleFailedSchedule(e);
					}
				}, initialDelay, period, delayUnit);
	}

	protected void handleFailedSchedule(Exception e) {
		logger.error("Failed to execute " + getClass().getSimpleName(), e);
	}
}
