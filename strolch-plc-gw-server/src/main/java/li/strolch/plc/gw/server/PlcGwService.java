package li.strolch.plc.gw.server;

import static li.strolch.plc.model.ModelHelper.valueToJson;
import static li.strolch.plc.model.PlcConstants.TYPE_PLC;
import static li.strolch.runtime.StrolchConstants.SYSTEM_USER_AGENT;
import static li.strolch.utils.helper.ExceptionHelper.getCallerMethod;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.execution.ExecutionHandler;
import li.strolch.handler.operationslog.OperationsLog;
import li.strolch.model.Resource;
import li.strolch.model.log.LogMessage;
import li.strolch.model.log.LogMessageState;
import li.strolch.model.log.LogSeverity;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcAddressResponse;
import li.strolch.plc.model.PlcServiceState;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.privilege.PrivilegedRunnable;
import li.strolch.runtime.privilege.PrivilegedRunnableWithResult;
import li.strolch.utils.CheckedBiFunction;
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

	public ComponentContainer getContainer() {
		return this.container;
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

	protected void register(String resource, String action) {
		PlcAddressKey addressKey = keyFor(resource, action);
		this.plcHandler.register(this.plcId, addressKey, this);
	}

	protected void register(PlcConnectionStateListener listener) {
		this.plcHandler.register(this.plcId, listener);
	}

	protected void unregister(PlcConnectionStateListener listener) {
		this.plcHandler.unregister(this.plcId, listener);
	}

	protected void register(PlcAddressKey key) {
		this.plcHandler.register(this.plcId, key, this);
	}

	protected void unregister(String resource, String action) {
		this.plcHandler.unregister(this.plcId, keyFor(resource, action), this);
	}

	protected void unregister(PlcAddressKey key) {
		this.plcHandler.unregister(this.plcId, key, this);
	}

	protected PlcAddressKey keyFor(String resource, String action) {
		return PlcAddressKey.keyFor(resource, action);
	}

	public void sendMessage(String resource, String action, Object value, PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(keyFor(resource, action), this.plcId, valueToJson(value), listener);
	}

	public void sendMessage(PlcAddressKey addressKey, Object value, PlcAddressResponseListener listener) {
		this.plcHandler.sendMessage(addressKey, this.plcId, value, listener);
	}

	public void readState(String resource, String action, PlcAddressResponseValueListener listener) {
		this.plcHandler.asyncGetAddressState(keyFor(resource, action), this.plcId, listener);
	}

	public void readState(PlcAddressKey addressKey, PlcAddressResponseValueListener listener) {
		this.plcHandler.asyncGetAddressState(addressKey, this.plcId, listener);
	}

	@Override
	public void handleResponse(PlcAddressResponse response) throws Exception {
		throw new UnsupportedOperationException("Not implemented!");
	}

	@Override
	public void handleNotification(PlcAddressKey addressKey, Object value) {
		throw new UnsupportedOperationException("Not implemented!");
	}

	protected boolean hasExecutionHandler() {
		return this.container.hasComponent(ExecutionHandler.class);
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

	protected void run(PrivilegedRunnable runnable) {
		try {
			this.plcHandler.run(runnable);
		} catch (Exception e) {
			handleFailedRunnable(runnable.toString(), e);
		}
	}

	protected <T> T run(PrivilegedRunnableWithResult<T> runnable) {
		try {
			return this.plcHandler.runWithResult(runnable);
		} catch (Exception e) {
			handleFailedRunnable(runnable.toString(), e);
			throw new IllegalStateException("Failed to execute runnable " + runnable, e);
		}
	}

	private void handleFailedRunnable(String runnable, Exception e) {
		logger.error("Runnable " + runnable + " failed!", e);
		if (hasOperationsLogs()) {
			getOperationsLogs().addMessage(
					new LogMessage(this.container.getRealmNames().iterator().next(), SYSTEM_USER_AGENT,
							Resource.locatorFor(TYPE_PLC, this.plcId), LogSeverity.Exception,
							LogMessageState.Information, PlcGwSrvI18n.bundle, "systemAction.failed").withException(e)
							.value("action", runnable));
		}
	}

	/**
	 * Executes the given consumer in a read-only transaction
	 *
	 * @param consumer
	 * 		the consumer to run in a read-only transaction
	 */
	protected <T> T runReadOnlyTx(CheckedBiFunction<PrivilegeContext, StrolchTransaction, T> consumer) {
		return run(ctx -> {
			try (StrolchTransaction tx = openTx(ctx, getCallerMethod(), true)) {
				return consumer.apply(ctx, tx);
			}
		});
	}

	/**
	 * <p>Executes the given consumer in a writeable transaction</p>
	 *
	 * <p><b>Note:</b> The transaction is automatically committed by calling {@link StrolchTransaction#commitOnClose()}
	 * when the runnable is completed and the TX is dirty, i.e. {@link StrolchTransaction#needsCommit()} returns
	 * true</p>
	 *
	 * @param consumer
	 * 		the consumer to run in a writeable transaction
	 */
	protected <T> T runWritableTx(CheckedBiFunction<PrivilegeContext, StrolchTransaction, T> consumer) {
		return run(ctx -> {
			try (StrolchTransaction tx = openTx(ctx, getCallerMethod(), false)) {
				try {
					T t = consumer.apply(ctx, tx);
					if (tx.needsCommit())
						tx.commitOnClose();
					return t;
				} catch (Exception e) {
					tx.rollbackOnClose();
					throw e;
				}
			}
		});
	}

	protected <T> T runWithResult(PrivilegedRunnableWithResult<T> runnable) throws Exception {
		return this.plcHandler.runWithResult(runnable);
	}

	protected StrolchTransaction openTx(PrivilegeContext ctx, String action, boolean readOnly) {
		return this.container.getRealm(ctx.getCertificate()).openTx(ctx.getCertificate(), action, readOnly);
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
		return this.container.getAgent()
				.getScheduledExecutor(PlcGwService.class.getSimpleName())
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
		return this.container.getAgent()
				.getScheduledExecutor(PlcGwService.class.getSimpleName())
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
