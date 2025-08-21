package top.turboweb.http.context;

import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.session.HttpSession;

/**
 * http上下文
 */
public interface HttpContext extends ParamBinder {

	HttpInfoRequest getRequest();

	ConnectSession getConnectSession();

	SseResponse createSseResponse();

	/**
	 * 获取session
	 *
	 * @return session
	 */
	HttpSession httpSession();

	HttpCookieManager cookie();

	/**
	 * 创建sse发射器
	 *
	 * @return sse发射器
	 */
	SseEmitter createSseEmitter();

	SseEmitter createSseEmitter(int maxMessageCache);
}
