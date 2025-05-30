package top.turboweb.http.context;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookie;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;


/**
 * 抽象的HttpContext的核心操作
 */
public abstract class CoreHttpContext implements HttpContext{

	protected final HttpInfoRequest request;
	protected final HttpSession session;
	protected final HttpInfoResponse response;
	protected final ConnectSession connectSession;
	private boolean isWrite = false;

	protected CoreHttpContext(HttpInfoRequest request, HttpSession httpSession, HttpInfoResponse response, ConnectSession connectSession) {
		this.request = request;
		this.session = httpSession;
		this.response = response;
		this.connectSession = connectSession;
	}

	@Override
	public HttpInfoRequest getRequest() {
		return this.request;
	}

	@Override
	public HttpInfoResponse getResponse() {
		return this.response;
	}

	@Override
	public ConnectSession getConnectSession() {
		return this.connectSession;
	}

	@Override
	public SseResponse newSseResponse() {
		return new SseResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, response.headers(), connectSession);
	}

	@Override
	public HttpCookie getHttpCookie() {
		return new HttpCookie(request.getCookies(), response);
	}

	@Override
	public HttpSession getHttpSession() {
		return this.session;
	}

	@Override
	public boolean isWrite() {
		return this.isWrite;
	}

	@Override
	public void setWrite() {
		this.isWrite = true;
	}
}
