package top.turboweb.http.response;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * 忽略的响应
 */
public class IgnoredHttpResponse extends DefaultHttpResponse implements InternalCallResponse{

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

	@Override
	public InternalCallType getType() {
		return InternalCallType.IGNORED;
	}
}
