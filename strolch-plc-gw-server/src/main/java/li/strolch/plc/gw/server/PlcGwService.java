package li.strolch.plc.gw.server;

import static li.strolch.plc.model.PlcConstants.TYPE_PLC;
import static li.strolch.runtime.StrolchConstants.SYSTEM_USER_AGENT;

import java.util.ResourceBundle;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.execution.ExecutionHandler;
import li.strolch.handler.operationslog.LogMessage;
import li.strolch.handler.operationslog.LogSeverity;
import li.strolch.handler.operationslog.OperationsLog;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressResponse;
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

	public PlcGwService(String plcId, PlcGwServerHandler plcHandler) {
		DBC.PRE.assertNotEmpty("plcId must be set!", plcId);
		DBC.PRE.assertNotNull("plcHandler must be set!", plcHandler);
		this.plcId = plcId;
		this.container = plcHandler.getContainer();
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
		this.plcHandler.register(this.plcId, key, this);
	}

	protected void unregister(PlcAddressKey key) {
		this.plcHandler.unregister(this.plcId, key, this);
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

	@Override
	public void handleResponse(PlcAddressResponse response) throws Exception {
		throw new UnsupportedOperationException("Not implemented!");
	}

	@Override
	public void handleNotification(PlcAddressKey addressKey, Object value) {
		throw new UnsupportedOperationException("Not implemented!");
	}

	protected StrolchTransaction openTx(PrivilegeContext ctx, boolean readOnly) {
		return this.container.getRealm(ctx.getCertificate()).openTx(ctx.getCertificate(), getClass(), readOnly);
	}

	protected void runAsAgent(PrivilegedRunnable runnable) {
		try {
			this.container.getPrivilegeHandler().runAsAgent(runnable);
		} catch (Exception e) {
			logger.error("Runnable " + runnable + " failed!", e);
			if (hasOperationsLogs()) {
				getOperationsLogs().addMessage(
						new LogMessage(this.container.getRealmNames().iterator().next(), SYSTEM_USER_AGENT,
								Resource.locatorFor(TYPE_PLC, this.plcId), LogSeverity.Exception,
								ResourceBundle.getBundle("strolch-plc-gw-server"), "systemAction.failed")
								.withException(e).value("action", runnable).value("reason", e));
			}
		}
	}

	protected ExecutionHandler getExecutionHandler() {
		return this.container.getComponent(ExecutionHandler.class);
	}

	protected boolean hasOperationsLogs() {
		return this.container.hasComponent(OperationsLog.class);
	}

	protected OperationsLog getOperationsLogs() {
		return this.container.getComponent(OperationsLog.class);
	}

	protected <T> T getComponent(Class<T> clazz) {
		return this.container.getComponent(clazz);
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
