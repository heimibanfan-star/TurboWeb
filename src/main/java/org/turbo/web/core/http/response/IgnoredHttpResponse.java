package org.turbo.web.core.http.response;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * 忽略的响应
 */
public class IgnoredHttpResponse extends DefaultHttpResponse {

	private static final IgnoredHttpResponse ignoredHttpResponse;

	static {
		ignoredHttpResponse = new IgnoredHttpResponse();
	}

	private IgnoredHttpResponse() {
		super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
	}

	public static IgnoredHttpResponse ignore() {
		return ignoredHttpResponse;
	}

}
