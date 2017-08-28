/*-
 * ========================LICENSE_START=================================
 * AEM Permission Management
 * %%
 * Copyright (C) 2013 Cognifide Limited
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package com.cognifide.cq.cqsm.core.executors;

import static com.cognifide.cq.cqsm.core.scripts.ScriptFilters.filterOnStart;

import com.cognifide.cq.cqsm.api.executors.Mode;
import com.cognifide.cq.cqsm.api.logger.Progress;
import com.cognifide.cq.cqsm.api.scripts.EventListener;
import com.cognifide.cq.cqsm.api.scripts.Script;
import com.cognifide.cq.cqsm.api.scripts.ScriptFinder;
import com.cognifide.cq.cqsm.api.scripts.ScriptManager;
import com.cognifide.cq.cqsm.core.Cqsm;
import com.cognifide.cq.cqsm.core.utils.MessagingUtils;
import com.cognifide.cq.cqsm.core.utils.sling.OperateCallback;
import com.cognifide.cq.cqsm.core.utils.sling.SlingHelper;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jcr.RepositoryException;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
@Properties({@Property(name = Constants.SERVICE_DESCRIPTION, value = "CQSM Startup Executor"),
		@Property(name = Constants.SERVICE_VENDOR, value = Cqsm.VENDOR_NAME)})
public class StartupExecutor {

	private static final Logger LOG = LoggerFactory.getLogger(StartupExecutor.class);

	/**
	 * Reference needed for proper event hook up on activation
	 */
	@Reference
	private EventListener eventListener;

	@Reference
	private ScriptManager scriptManager;

	@Reference
	private ScriptFinder scriptFinder;

	@Reference
	private ResourceResolverFactory resolverFactory;
	
	@Reference
	private SlingSettingsService slingSettingsService;

	@Activate
	private synchronized void activate(ComponentContext ctx) {
		SlingHelper.operateTraced(resolverFactory, new OperateCallback() {
			@Override
			public void operate(ResourceResolver resolver) throws Exception {
				runOnStartup(resolver);
			}
		});
	}

	private void runOnStartup(ResourceResolver resolver) throws PersistenceException {
		final List<Script> scripts = scriptFinder.findAll(filterOnStart(resolver), resolver);
		if (scripts.size() > 0) {
			LOG.info("Startup script executor is trying to execute scripts on startup: {}", scripts.size());
			LOG.info(MessagingUtils.describeScripts(scripts));
			for (Script script : scripts) {
				if (isAllowedToExecute(script.getPath())) {
					runScript(resolver, script);
				}
			}
		} else {
			LOG.info("Startup script executor has nothing to do");
		}
	}

	private void runScript(ResourceResolver resolver, Script script) throws PersistenceException {
		final String scriptPath = script.getPath();

		try {
			scriptManager.process(script, Mode.VALIDATION, resolver);
			if (script.isValid()) {
				final Progress progress = scriptManager.process(script, Mode.AUTOMATIC_RUN, resolver);
				logStatus(scriptPath, progress.isSuccess());
			} else {
				LOG.warn("Startup executor cannot execute script which is not valid: {}", scriptPath);
			}
		} catch (RepositoryException e) {
			LOG.error("Script cannot be processed because of repository error: {}", e);
		}
	}

	private void logStatus(String scriptPath, boolean success) {
		if (success) {
			LOG.info("Script successfully executed: {}", scriptPath);
		} else {
			LOG.error("Script cannot be executed properly: {}", scriptPath);
		}
	}

	private boolean isAllowedToExecute(String path) {
		boolean isAllowed = false;
		if (!path.contains("config.")) {
			isAllowed = true;
		} else {
			Pattern pattern = Pattern.compile("config.+/");
			Matcher matcher = pattern.matcher(path);
			if (matcher.find()) {
				String config = matcher.group(0);
				String instance = StringUtils.removeStart(config, "config.").replaceAll("/", "");
				isAllowed = slingSettingsService.getRunModes().contains(instance);
			}
		}
		return isAllowed;
	}
}
