package top.heimi.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.listener.TurboWebListener;

/**
 * TODO
 */
public class FirstListener implements TurboWebListener {
	private static final Logger log = LoggerFactory.getLogger(FirstListener.class);

	@Override
	public void beforeServerInit() {
		log.info("FirstListener beforeServerInit");
	}

	@Override
	public void afterServerStart() {
		log.info("FirstListener afterServerStart");
	}
}
