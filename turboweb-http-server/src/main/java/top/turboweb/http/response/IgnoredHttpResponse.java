package top.turboweb.http.response;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.http.orchestrator.IgnoredResponseWriter;
import top.turboweb.http.orchestrator.ResponseWriter;

/**
 * 表示被忽略的 HTTP 响应。
 * <p>
 * 用于内部调用场景中不需要实际响应内容的情况。
 * 该类实现 {@link ResponseCall} 接口，声明由 {@link IgnoredResponseWriter} 负责写出，并且使用单例模式，
 * 避免重复创建对象。
 */
public class IgnoredHttpResponse extends DefaultHttpResponse implements ResponseCall{

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
	public Class<? extends ResponseWriter> getWriterType() {
		return IgnoredResponseWriter.class;
	}
}
