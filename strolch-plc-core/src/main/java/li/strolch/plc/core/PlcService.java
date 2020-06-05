package li.strolch.plc.core;

import static li.strolch.plc.model.PlcConstants.PARAM_VALUE;
import static li.strolch.plc.model.PlcConstants.TYPE_PLC_ADDRESS;
import static li.strolch.runtime.StrolchConstants.DEFAULT_REALM;
import static li.strolch.utils.helper.ExceptionHelper.getCallerMethod;

import java.util.ResourceBundle;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.model.log.LogMessage;
import li.strolch.model.log.LogMessageState;
import li.strolch.model.log.LogSeverity;
import li.strolch.model.Locator;
import li.strolch.model.Resource;
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

	public void register(String resource, String action) {
		this.plcHandler.register(resource, action, this);
	}

	public void unregister(String resource, String action) {
		this.plcHandler.unregister(resource, action, this);
	}

	protected Resource getPlcAddress(StrolchTransaction tx, String resource, String action) {
		String plcAddressId = this.plcHandler.getPlcAddressId(resource, action);
		return tx.getResourceBy(TYPE_PLC_ADDRESS, plcAddressId, true);
	}

	protected <T> T getAddressState(StrolchTransaction tx, String resource, String action) {
		Parameter<T> addressParam = getPlcAddress(tx, resource, action).getParameter(PARAM_VALUE, true);
		return addressParam.getValue();
	}

	protected void enableMsg(PlcAddressKey addressKey, ResourceBundle bundle, LogSeverity severity) {
		sendMsg(logMessageFor(addressKey, bundle, severity, LogMessageState.Active));
	}

	protected void disableMsg(PlcAddressKey addressKey) {
		disableMsg(Locator.valueOf("Plc", this.plcHandler.getPlcId(), addressKey.resource, addressKey.action));
	}

	protected void enableMsg(String i18nKey, ResourceBundle bundle, LogSeverity severity) {
		sendMsg(logMessageFor(i18nKey, bundle, severity, LogMessageState.Active));
	}

	protected void disableMsg(String i18nKey, ResourceBundle bundle) {
		disableMsg(Locator.valueOf("Plc", this.plcHandler.getPlcId(), bundle.getBaseBundleName(), i18nKey));
	}

	protected void sendMsg(String i18nKey, ResourceBundle bundle, LogSeverity severity) {
		sendMsg(logMessageFor(i18nKey, bundle, severity));
	}

	protected LogMessage logMessageFor(PlcAddressKey addressKey, ResourceBundle bundle, LogSeverity severity) {
		return logMessageFor(addressKey, bundle, severity, LogMessageState.Information);
	}

	protected LogMessage logMessageFor(PlcAddressKey addressKey, ResourceBundle bundle, LogSeverity severity,
			LogMessageState state) {
		return new LogMessage(DEFAULT_REALM, this.plcHandler.getPlcId(),
				Locator.valueOf("Plc", this.plcHandler.getPlcId(), addressKey.resource, addressKey.action), severity,
				state, bundle, addressKey.toKey());
	}

	protected LogMessage logMessageFor(String i18nKey, ResourceBundle bundle, LogSeverity severity) {
		return logMessageFor(i18nKey, bundle, severity, LogMessageState.Information);
	}

	protected LogMessage logMessageFor(String i18nKey, ResourceBundle bundle, LogSeverity severity,
			LogMessageState state) {
		return new LogMessage(DEFAULT_REALM, this.plcHandler.getPlcId(),
				Locator.valueOf("Plc", this.plcHandler.getPlcId(), bundle.getBaseBundleName(), i18nKey), severity,
				state, bundle, i18nKey);
	}

	protected void sendMsg(LogMessage logMessage) {
		this.plcHandler.sendMsg(logMessage);
	}

	protected void disableMsg(Locator locator) {
		this.plcHandler.disableMsg(locator);
	}

	protected void send(String resource, String action) {
		this.plcHandler.send(resource, action);
	}

	protected void send(String resource, String action, Object value) {
		this.plcHandler.send(resource, action, value);
	}

	protected void notify(String resource, String action, Object value) {
		this.plcHandler.notify(resource, action, value);
	}

	protected PlcAddressKey keyFor(String resource, String action) {
		return PlcAddressKey.keyFor(resource, action);
	}

	protected void run(PrivilegedRunnable runnable) throws Exception {
		this.container.getPrivilegeHandler().runAsAgent(runnable);
	}

	protected <T> T runWithResult(PrivilegedRunnableWithResult<T> runnable) throws Exception {
		return this.container.getPrivilegeHandler().runAsAgentWithResult(runnable);
	}

	protected StrolchTransaction openTx(PrivilegeContext ctx, boolean readOnly) {
		return this.container.getRealm(ctx.getCertificate()).openTx(ctx.getCertificate(), getCallerMethod(2), readOnly);
	}

	private ScheduledExecutorService getScheduledExecutor() {
		return this.container.getAgent().getScheduledExecutor(PlcService.class.getSimpleName());
	}

	protected ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit delayUnit) {
		return getScheduledExecutor().schedule(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, delay, delayUnit);
	}

	protected ScheduledFuture<?> schedule(PrivilegedRunnable runnable, long delay, TimeUnit delayUnit) {
		return getScheduledExecutor().schedule(() -> {
			try {
				this.container.getPrivilegeHandler().runAsAgent(runnable);
			} catch (Exception e) {
				handleFailedSchedule(e);
			}
		}, delay, delayUnit);
	}

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

	protected void handleFailedSchedule(Exception e) {
		logger.error("Failed to execute " + getClass().getSimpleName(), e);
	}
}
