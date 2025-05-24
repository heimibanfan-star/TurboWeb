package top.heimi.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.listener.TurboWebListener;

/**
 * TODO
 */
public class SecondListener implements TurboWebListener {
	private static final Logger log = LoggerFactory.getLogger(SecondListener.class);

	@Override
	public void beforeServerInit() {
		log.info("SecondListener.beforeServerInit");
	}

	@Override
	public void afterServerStart() {
		log.info("SecondListener.afterServerStart");
	}
}
