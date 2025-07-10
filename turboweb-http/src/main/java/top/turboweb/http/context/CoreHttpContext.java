package top.turboweb.http.context;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.session.HttpSession;

import java.util.Objects;


/**
 * 抽象的HttpContext的核心操作
 */
public abstract class CoreHttpContext implements HttpContext{

	protected final HttpInfoRequest request;
	protected final HttpSession session;
	protected final ConnectSession connectSession;
	private final HttpCookieManager httpCookieManager;

	protected CoreHttpContext(HttpInfoRequest request, HttpSession httpSession, HttpCookieManager cookieManager, ConnectSession connectSession) {
		this.request = request;
		this.session = httpSession;
		this.httpCookieManager = cookieManager;
		this.connectSession = connectSession;
	}

	@Override
	public HttpInfoRequest getRequest() {
		return this.request;
	}

	@Override
	public ConnectSession getConnectSession() {
		return this.connectSession;
	}

	@Override
	public SseResponse createSseResponse() {
		return new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(), connectSession);
	}

	@Override
	public SseEmitter createSseEmitter() {
		return new InternalSseEmitter(connectSession, 32);
	}

	@Override
	public SseEmitter createSseEmitter(int maxMessageCache) {
		return new InternalSseEmitter(connectSession, maxMessageCache);
	}


	@Override
	public HttpSession httpSession() {
		return this.session;
	}

	@Override
	public HttpCookieManager cookie() {
		return this.httpCookieManager;
	}
}
