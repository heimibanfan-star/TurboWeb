package org.turboweb.core.http.context;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.turboweb.core.connect.ConnectSession;
import org.turboweb.core.http.cookie.HttpCookie;
import org.turboweb.core.http.request.HttpInfoRequest;
import org.turboweb.core.http.response.HttpInfoResponse;
import org.turboweb.core.http.response.SseResponse;
import org.turboweb.core.http.session.Session;


/**
 * 抽象的HttpContext的核心操作
 */
public abstract class CoreHttpContext implements HttpContext{

	protected final HttpInfoRequest request;
	protected final HttpInfoResponse response;
	protected final ConnectSession connectSession;
	private boolean isWrite = false;

	protected CoreHttpContext(HttpInfoRequest request, HttpInfoResponse response, ConnectSession connectSession) {
		this.request = request;
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
	public Session getSession() {
		return request.getSession();
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
