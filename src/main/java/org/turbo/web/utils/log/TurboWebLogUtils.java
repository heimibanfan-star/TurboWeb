package org.turbo.web.utils.log;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 加载turboWeb的logback.xml
 */
public class TurboWebLogUtils {

	private TurboWebLogUtils() {}

	public static void simpleLog() {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		try {
			context.reset();
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			configurator.doConfigure(Objects.requireNonNull(TurboWebLogUtils.class.getClassLoader().getResource("logback-turbo.xml")));
		} catch (JoranException e) {
			System.err.println("加载turboWeb的logback.xml失败");
		}
		StatusManager statusManager = context.getStatusManager();
		if (statusManager != null) {
			for (Status status : statusManager.getCopyOfStatusList()) {
				if (status.getLevel() >= Status.WARN) {
					System.err.println("[Logback] " + status.getMessage());
					if (status.getThrowable() != null) {
						status.getThrowable().printStackTrace(System.err);
					}
				}
			}
		}
	}
}
