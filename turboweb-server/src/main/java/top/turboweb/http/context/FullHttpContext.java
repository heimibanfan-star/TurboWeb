package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;

/**
 * {@code FullHttpContext} 是 TurboWeb 框架中最完整的 HTTP 上下文实现。
 * <p>
 * 它在 {@link FileHttpContext} 的基础上，进一步整合了：
 * <ul>
 *     <li>表单与 JSON 请求体反序列化</li>
 * </ul>
 * <p>
 * 该类主要面向业务层使用者，提供统一的参数访问与数据绑定能力。
 * 它不关心路由分发或请求调度，仅负责当前请求作用域的数据封装与访问。
 *
 * @see FileHttpContext
 * @see HttpContext
 */
public class FullHttpContext extends FileHttpContext implements HttpContext{

	/**
	 * 构造完整的 HTTP 上下文。
	 *
	 * @param request        HTTP 请求对象
	 * @param httpSession    会话对象
	 * @param cookies  当前请求的 Cookie 集合
	 * @param connectSession 连接会话（用于 WebSocket / SSE）
	 * @param jsonSerializer JSON 序列化器
	 * @param defaultCharset 缺省编码（装配期由 HttpConfig 判定并随请求传递），不能为 null
	 */
	public FullHttpContext(
			FullHttpRequest request,
			HttpSession httpSession,
			HttpCookies cookies,
			ConnectSession connectSession,
			JsonSerializer jsonSerializer,
			Charset defaultCharset
	) {
		super(request, httpSession, cookies, connectSession, jsonSerializer, defaultCharset);
	}

	/**
	 * 加载并反序列化表单参数。
	 */
	@Override
	public <T> T loadForm(Class<T> beanType) {
		return bindBean(httpContent.getFormParams(), beanType);
	}

	/**
	 * 反序列化 JSON 请求体为对象。
	 *
	 * @throws RequestParseException 当请求体为空时抛出
	 */
	@Override
	public <T> T loadJson(Class<T> beanType) {
		// 获取json请求体
		String jsonContent = httpContent.getJsonContent();
		if (jsonContent == null) {
			throw new RequestParseException("JSON request body is empty");
		}
		// 序列化对象
		return jsonSerializer.jsonToBean(jsonContent, beanType);
	}

	/**
	 * 释放上下文资源。
	 * <p>
	 * 通常在请求结束后由框架自动调用。
	 */
	@Override
	public void release() {
		httpContent.release();
	}
}
