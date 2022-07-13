package li.strolch.plc.core;

import static li.strolch.plc.model.PlcConstants.PARAM_VALUE;
import static li.strolch.plc.model.PlcConstants.TYPE_PLC_ADDRESS;
import static li.strolch.runtime.StrolchConstants.DEFAULT_REALM;
import static li.strolch.utils.helper.ExceptionHelper.getCallerMethod;

import java.util.ResourceBundle;
import java.util.concurrent.*;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.model.Locator;
import li.strolch.model.Resource;
import li.strolch.model.log.LogMessage;
import li.strolch.model.log.LogMessageState;
import li.strolch.model.log.LogSeverity;
import li.strolch.model.parameter.Parameter;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.plc.core.hw.PlcListener;
import li.strolch.plc.model.PlcAddress;
import li.strolch.plc.model.PlcAddressKey;
import li.strolch.plc.model.PlcServiceState;
import li.strolch.privilege.model.PrivilegeContext;
import li.strolch.runtime.privilege.PrivilegedRunnable;
import li.strolch.runtime.privilege.PrivilegedRunnableWithResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This is an interface to implement short use cases in a plc.</p>
 *
 * <p>Each instance of a {@link PlcService} should correspond with one resource on a {@link PlcAddress#resource}.</p>
 *
 * <p>The service registers for changes on the hardware, e.g. button presses, light barriers, etc. and then performs a
 * given action, e.g. turning a motor on or off.</p>
 *
 * <p>The PlcService has the following life cycle:</p>
 * <ul>
 *     <li>{@link #register()}</li>
 *     <li>{@link #start(StrolchTransaction)}</li>
 *     <li>{@link #stop()}</li>
 *     <li>{@link #unregister()}</li>
 * </ul>
 */
public abstract class PlcService implements PlcListener {

	protected static final Logger logger = LoggerFactory.getLogger(PlcService.class);
	protected final ComponentContainer container;
	protected final PlcHandler plcHandler;

	private PlcServiceState state;

	public PlcService(PlcHandler plcHandler) {
		this.container = plcHandler.getContainer();
		this.plcHandler = plcHandler;
		this.state = PlcServiceState.Unregistered;
	}

	public PlcServiceState getState() {
		return this.state;
	}

	@Override
	public void handleNotification(PlcAddress address, Object value) {
		// no-op
	}

	/**
	 * Called to initialize this service, here one would read the model state of a given address using
	 * {@link #getAddressState(StrolchTransaction, String, String)}
	 *
	 * @param tx
	 * 		the transaction giving access to the model
	 */
	public void start(StrolchTransaction tx) {
		this.state = PlcServiceState.Started;
	}

	/**
	 * Called to stop the plc service. Here you would cancel any scheduled tasks.
	 */
	public void stop() {
		this.state = PlcServiceState.Stopped;
	}

	/**
	 * Called to register this service for all relevant {@link PlcAddress}
	 */
	public void register() {
		this.state = PlcServiceState.Registered;
	}

	/**
	 * Called to unregister this service from previously registered addresses
	 */
	public void unregister() {
		this.state = PlcServiceState.Unregistered;
	}

	/**
	 * Register this service with the given resource and action
	 *
	 * @param resource
	 * 		the resource ID
	 * @param action
	 * 		the action
	 */
	public void register(String resource, String action) {
		this.plcHandler.register(resource, action, this);
	}

	/**
	 * Unregister this service with the given resource and action
	 *
	 * @param resource
	 * 		the resource ID
	 * @param action
	 * 		the action
	 */
	public void unregister(String resource, String action) {
		this.plcHandler.unregister(resource, action, this);
	}

	/**
	 * Returns the {@link Resource} of type #TYPE_PLC_ADDRESS for the given resource and action
	 *
	 * @param tx
	 * 		the current TX
	 * @param resource
	 * 		the resource
	 * @param action
	 * 		the action
	 *
	 * @return the {@link Resource}
	 */
	protected Resource getPlcAddress(StrolchTransaction tx, String resource, String action) {
		String plcAddressId = this.plcHandler.getPlcAddressId(resource, action);
		return tx.getResourceBy(TYPE_PLC_ADDRESS, plcAddressId, true);
	}

	/**
	 * Returns the value of a plc address by calling {@link #getPlcAddress(StrolchTransaction, String, String)} for the
	 * given resource and action
	 *
	 * @param tx
	 * 		the current TX
	 * @param resource
	 * 		the resource
	 * @param action
	 * 		the action
	 * @param <T>
	 * 		the type of value to return
	 *
	 * @return the value of the given address
	 */
	protected <T> T getAddressState(StrolchTransaction tx, String resource, String action) {
		Parameter<T> addressParam = getPlcAddress(tx, resource, action).getParameter(PARAM_VALUE, true);
		return addressParam.getValue();
	}

	/**
	 * Enables an operations log message to be seen by a user
	 *
	 * @param addressKey
	 * 		the address for which the message is enabled
	 * @param bundle
	 * 		the resource bundle containing the message
	 * @param severity
	 * 		the severity of the message
	 */
	protected void enableMsg(PlcAddressKey addressKey, ResourceBundle bundle, LogSeverity severity) {
		sendMsg(logMessageFor(addressKey, bundle, severity, LogMessageState.Active));
	}

	/**
	 * Disables an operations log message which was previously enabled
	 *
	 * @param addressKey
	 * 		the address for which the message was enabled
	 */
	protected void disableMsg(PlcAddressKey addressKey) {
		disableMsg(Locator.valueOf("Plc", this.plcHandler.getPlcId(), addressKey.resource, addressKey.action));
	}

	/**
	 * Enables an operations log message to be seen by a user
	 *
	 * @param i18nKey
	 * 		the key of the message in the resource bundle
	 * @param bundle
	 * 		the resource bundle containing the message
	 * @param severity
	 * 		the severity of the message
	 */
	protected void enableMsg(String i18nKey, ResourceBundle bundle, LogSeverity severity) {
		sendMsg(logMessageFor(i18nKey, bundle, severity, LogMessageState.Active));
	}

	/**
	 * Disables an operations log message which was previously enabled
	 *
	 * @param i18nKey
	 * 		the key of the message in the resource bundle for which the message was enabled
	 * @param bundle
	 * 		the resource bundle containing the message
	 */
	protected void disableMsg(String i18nKey, ResourceBundle bundle) {
		disableMsg(Locator.valueOf("Plc", this.plcHandler.getPlcId(), bundle.getBaseBundleName(), i18nKey));
	}

	/**
	 * Sends a message created for the given properties to a remote listener
	 *
	 * @param i18nKey
	 * 		the key of the message
	 * @param bundle
	 * 		the bundle containing the key
	 * @param severity
	 * 		the severity of the message
	 */
	protected void sendMsg(String i18nKey, ResourceBundle bundle, LogSeverity severity) {
		sendMsg(logMessageFor(i18nKey, bundle, severity));
	}

	/**
	 * Creates a {@link LogMessage} for the given fields
	 *
	 * @param addressKey
	 * 		the address for the key
	 * @param bundle
	 * 		the bundle containing the message
	 * @param severity
	 * 		the severity of the message
	 *
	 * @return the {@link LogMessage} instance
	 */
	protected LogMessage logMessageFor(PlcAddressKey addressKey, ResourceBundle bundle, LogSeverity severity) {
		return logMessageFor(addressKey, bundle, severity, LogMessageState.Information);
	}

	/**
	 * Creates a {@link LogMessage} for the given fields
	 *
	 * @param addressKey
	 * 		the address for the key
	 * @param bundle
	 * 		the bundle containing the message
	 * @param severity
	 * 		the severity of the message
	 * @param state
	 * 		the state of the message
	 *
	 * @return the {@link LogMessage} instance
	 */
	protected LogMessage logMessageFor(PlcAddressKey addressKey, ResourceBundle bundle, LogSeverity severity,
			LogMessageState state) {
		return new LogMessage(DEFAULT_REALM, this.plcHandler.getPlcId(),
				Locator.valueOf("Plc", this.plcHandler.getPlcId(), addressKey.resource, addressKey.action), severity,
				state, bundle, addressKey.toKey());
	}

	/**
	 * Creates a {@link LogMessage} for the given fields
	 *
	 * @param i18nKey
	 * 		the key of the message
	 * @param bundle
	 * 		the bundle containing the message
	 * @param severity
	 * 		the severity of the message
	 *
	 * @return the {@link LogMessage} instance
	 */
	protected LogMessage logMessageFor(String i18nKey, ResourceBundle bundle, LogSeverity severity) {
		return logMessageFor(i18nKey, bundle, severity, LogMessageState.Information);
	}

	/**
	 * Creates a {@link LogMessage} for the given fields
	 *
	 * @param i18nKey
	 * 		the key of the message
	 * @param bundle
	 * 		the bundle containing the message
	 * @param severity
	 * 		the severity of the message
	 * @param state
	 * 		the state of the message
	 *
	 * @return the {@link LogMessage} instance
	 */
	protected LogMessage logMessageFor(String i18nKey, ResourceBundle bundle, LogSeverity severity,
			LogMessageState state) {
		return new LogMessage(DEFAULT_REALM, this.plcHandler.getPlcId(),
				Locator.valueOf("Plc", this.plcHandler.getPlcId(), bundle.getBaseBundleName(), i18nKey), severity,
				state, bundle, i18nKey);
	}

	/**
	 * Sends the given {@link LogMessage} to the remote listener
	 *
	 * @param logMessage
	 * 		the message to send
	 */
	protected void sendMsg(LogMessage logMessage) {
		switch (logMessage.getSeverity()) {
		case Info, Notification -> logger.info(logMessage.toString());
		case Warning -> logger.warn(logMessage.toString());
		case Error, Exception -> logger.error(logMessage.toString());
		}
		this.plcHandler.sendMsg(logMessage);
	}

	/**
	 * Disables a message with the given {@link Locator}
	 *
	 * @param locator
	 * 		the locator of the message
	 */
	protected void disableMsg(Locator locator) {
		logger.info("Disabling message for locator " + locator);
		this.plcHandler.disableMsg(locator);
	}

	/**
	 * Causes the {@link PlcAddress} for the given resource and action to be sent as a telegram with its default value
	 *
	 * @param resource
	 * 		the resource
	 * @param action
	 * 		the action
	 */
	protected void send(String resource, String action) {
		this.plcHandler.send(resource, action);
	}

	/**
	 * Causes the {@link PlcAddress} for the given resource and action to be sent as a telegram with the given value
	 *
	 * @param resource
	 * 		the resource
	 * @param action
	 * 		the action
	 * @param value
	 * 		the value to send with the {@link PlcAddress}
	 */
	protected void send(String resource, String action, Object value) {
		this.plcHandler.send(resource, action, value);
	}

	/**
	 * Notifies listeners on the {@link PlcAddress} for the given resource and action, of the new value
	 *
	 * @param resource
	 * 		the resource
	 * @param action
	 * 		the action
	 * @param value
	 * 		the value to notify the listeners with
	 */
	protected void notify(String resource, String action, Object value) {
		this.plcHandler.notify(resource, action, value);
	}

	/**
	 * Runs the given {@link PrivilegedRunnable} as the agent user
	 *
	 * @param runnable
	 * 		the runnable to run
	 */
	protected void run(PrivilegedRunnable runnable) throws Exception {
		this.container.getPrivilegeHandler().runAsAgent(runnable);
	}

	/**
	 * Runs the given {@link PrivilegedRunnableWithResult} as the agent user, returning a value as the result
	 *
	 * @param runnable
	 * 		the runnable to run
	 * @param <T>
	 * 		the type of object being returned in the runnable
	 *
	 * @return the result of the runnable
	 */
	protected <T> T runWithResult(PrivilegedRunnableWithResult<T> runnable) throws Exception {
		return this.container.getPrivilegeHandler().runAsAgentWithResult(runnable);
	}

	/**
	 * Opens a new {@link StrolchTransaction} with the given {@link PrivilegeContext}
	 *
	 * @param ctx
	 * 		the {@link PrivilegeContext}
	 * @param readOnly
	 * 		true for the TX to be read only
	 *
	 * @return the new TX to be used in a try-with-resource block
	 */
	protected StrolchTransaction openTx(PrivilegeContext ctx, boolean readOnly) {
		return this.container.getRealm(ctx.getCertificate()).openTx(ctx.getCertificate(), getCallerMethod(2), readOnly);
	}

	/**
	 * Returns the {@link ExecutorService} for this class
	 *
	 * @return the executor
	 */
	private ExecutorService getExecutor() {
		return this.container.getAgent().getExecutor(getClass().getSimpleName());
	}

	/**
	 * Returns the {@link ScheduledExecutorService} for this class
	 *
	 * @return the scheduled executor
	 */
	private ScheduledExecutorService getScheduledExecutor() {
		return this.container.getAgent().getScheduledExecutor(getClass().getSimpleName());
	}

	/**
	 * Submits the given runnable for asynchronous execution
	 *
	 * @param runnable
	 * 		the runnable to execute asynchronously
	 */
	protected void async(Runnable runnable) {
		getExecutor().submit(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		});
	}

	/**
	 * Delay the execution of the given {@link Runnable} by the given delay
	 *
	 * @param runnable
	 * 		the runnable to delay
	 * @param delay
	 * 		the time to delay
	 * @param delayUnit
	 * 		the unit of the time to delay
	 *
	 * @return a future to cancel the executor before execution
	 */
	protected ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit delayUnit) {
		return getScheduledExecutor().schedule(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, delay, delayUnit);
	}

	/**
	 * Delay the execution of the given {@link PrivilegedRunnable} by the given delay
	 *
	 * @param runnable
	 * 		the runnable to delay
	 * @param delay
	 * 		the time to delay
	 * @param delayUnit
	 * 		the unit of the time to delay
	 *
	 * @return a future to cancel the executor before execution
	 */
	protected ScheduledFuture<?> schedule(PrivilegedRunnable runnable, long delay, TimeUnit delayUnit) {
		return getScheduledExecutor().schedule(() -> {
			try {
				this.container.getPrivilegeHandler().runAsAgent(runnable);
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, delay, delayUnit);
	}

	/**
	 * Submit the given {@link Runnable} for repeated execution
	 *
	 * @param runnable
	 * 		the runnable to delay
	 * @param initialDelay
	 * 		the initial delay
	 * @param period
	 * 		the delay between subsequent executions
	 * @param delayUnit
	 * 		the unit of the time to delay
	 *
	 * @return a future to cancel the executor before execution
	 */
	protected ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period,
			TimeUnit delayUnit) {
		return getScheduledExecutor().scheduleAtFixedRate(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, initialDelay, period, delayUnit);
	}

	/**
	 * Submit the given {@link PrivilegedRunnable} for repeated execution
	 *
	 * @param runnable
	 * 		the runnable to delay
	 * @param initialDelay
	 * 		the initial delay
	 * @param period
	 * 		the delay between subsequent executions
	 * @param delayUnit
	 * 		the unit of the time to delay
	 *
	 * @return a future to cancel the executor before execution
	 */
	protected ScheduledFuture<?> scheduleAtFixedRate(PrivilegedRunnable runnable, long initialDelay, long period,
			TimeUnit delayUnit) {
		return getScheduledExecutor().scheduleAtFixedRate(() -> {
			try {
				this.container.getPrivilegeHandler().runAsAgent(runnable);
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, initialDelay, period, delayUnit);
	}

	/**
	 * Submit the given {@link Runnable} for repeated execution
	 *
	 * @param runnable
	 * 		the runnable to delay
	 * @param initialDelay
	 * 		the initial delay
	 * @param period
	 * 		the delay between subsequent executions
	 * @param delayUnit
	 * 		the unit of the time to delay
	 *
	 * @return a future to cancel the executor before execution
	 */
	protected ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long period,
			TimeUnit delayUnit) {
		return getScheduledExecutor().scheduleWithFixedDelay(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, initialDelay, period, delayUnit);
	}

	/**
	 * Submit the given {@link PrivilegedRunnable} for repeated execution
	 *
	 * @param runnable
	 * 		the runnable to delay
	 * @param initialDelay
	 * 		the initial delay
	 * @param period
	 * 		the delay between subsequent executions
	 * @param delayUnit
	 * 		the unit of the time to delay
	 *
	 * @return a future to cancel the executor before execution
	 */
	protected ScheduledFuture<?> scheduleWithFixedDelay(PrivilegedRunnable runnable, long initialDelay, long period,
			TimeUnit delayUnit) {
		return getScheduledExecutor().scheduleWithFixedDelay(() -> {
			try {
				this.container.getPrivilegeHandler().runAsAgent(runnable);
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, initialDelay, period, delayUnit);
	}

	/**
	 * Notifies the caller of one of the async, or schedule methods that the execution of a runnable failed
	 *
	 * @param e
	 * 		the exception which occurred
	 */
	protected void handleFailedSchedule(Exception e) {
		logger.error("Failed to execute " + getClass().getSimpleName(), e);
	}
}
