/*
 * Copyright (c) 2013-2025 Robert von Burg <eitch@eitchnet.ch>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package li.strolch.plc.core;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.agent.impl.SimplePostInitializer;
import li.strolch.handler.mail.MailHandler;
import li.strolch.job.StrolchJobsHandler;
import li.strolch.policy.ReloadPoliciesJob;
import li.strolch.policy.ReloadPrivilegeHandlerJob;
import li.strolch.runtime.configuration.RuntimeConfiguration;
import li.strolch.utils.helper.ExceptionHelper;

import static java.text.MessageFormat.format;

public class PlcPostInitializer extends SimplePostInitializer {

	public PlcPostInitializer(ComponentContainer container, String componentName) {
		super(container, componentName);
	}

	@Override
	public void start() throws Exception {

		registerJobs();
		notifyStart();

		super.start();
	}

	@Override
	public void stop() throws Exception {
		super.stop();
	}

	protected void registerJobs() throws Exception {
		if (!getContainer().hasComponent(StrolchJobsHandler.class))
			return;

		StrolchJobsHandler jobsHandler = getComponent(StrolchJobsHandler.class);

		// Manually triggered jobs to run once on startup
		// jobsHandler.register(XXX.class).runNow();

		// special jobs which are triggered by an admin, and not run at startup
		jobsHandler.register(ReloadPoliciesJob.class);
		jobsHandler.register(ReloadPrivilegeHandlerJob.class);

		// recurring jobs
		// jobsHandler.registerAndScheduleJob(XXX.class);
	}

	protected void notifyStart() {

		if (!(
				getConfiguration().getBoolean("notifyStart", Boolean.FALSE) && getContainer().hasComponent(
						MailHandler.class)))
			return;

		String recipients = getConfiguration().getString("notifyStartRecipients", "");
		if (recipients.isEmpty()) {
			logger.error("config param notifyStartRecipients is empty, can not notify of boot!");
			return;
		}

		StrolchAgent agent = getContainer().getAgent();
		RuntimeConfiguration runtimeConfiguration = agent.getStrolchConfiguration().getRuntimeConfiguration();
		String subject = format("{0}:{1} Startup Complete!", runtimeConfiguration.getApplicationName(),
				runtimeConfiguration.getEnvironment());

		String body = format(
				"Dear User\n\nThe {0} Server has just completed startup with version {1}\n\n\tYour Server.",
				getConfiguration().getRuntimeConfiguration().getApplicationName(),
				agent.getVersion().getAppVersion().getArtifactVersion());

		try {
			getContainer().getComponent(MailHandler.class).sendMailAsync(recipients, subject, body);
		} catch (Exception e) {
			logger.error("Notifying of server startup failed: {}", ExceptionHelper.getRootCause(e), e);
		}
	}
}
