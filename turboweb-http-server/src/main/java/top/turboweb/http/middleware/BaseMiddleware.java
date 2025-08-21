package top.turboweb.http.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 抽象的中间件
 */
public class BaseMiddleware {

	private static final Logger log = LoggerFactory.getLogger(BaseMiddleware.class);
	private Middleware next;
	private boolean isLock = false;

	public Middleware getNext() {
		return next;
	}

	public final void setNext(Middleware next) {
		if (isLock) {
			log.warn("Middleware is locked, can not set next middleware");
		} else {
			this.next = next;
		}
	}

	/**
	 * 锁定中间件，不允许设置下一个中间件
	 */
	public final void lockMiddleware() {
		isLock = true;
	}

	/**
	 * 初始化
	 *
	 * @param chain 中间件链
	 */
	public void init(Middleware chain) {
	}
}
