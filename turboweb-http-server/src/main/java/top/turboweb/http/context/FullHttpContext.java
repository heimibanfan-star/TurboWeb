package top.turboweb.http.context;

import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.context.attchment.DefaultAttachment;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.rest.core.HttpRequestMessage;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * {@code FullHttpContext} 是 TurboWeb 框架中 {@link HttpContext} 的唯一实现。
 * <p>
 * 它封装请求消息（{@link HttpRequestMessage}，承载请求方式、请求头、请求地址与请求体内容）、
 * Session、Cookie、连接会话、缺省编码与挂载对象；
 * 请求体参数的封装由内置解析器产出的具体内容类承载（表单字段与上传文件在
 * {@link HttpFormContent}、JSON 体字符串在 {@link HttpJsonContent}），读取侧认其具体类转型取数，
 * 本类不分派内容。
 * <p>
 * 本类不持有 Netty 的请求对象：请求的方式、头、地址与请求体内容统一经
 * {@link HttpRequestMessage} 读取，由编排器在装配上下文时就位。
 * 本类也不负责任何资源的关闭与释放：请求体内容的资源由创建方（请求编排器）
 * 在请求处理结束时统一释放。
 * <p>
 * 该类主要面向业务层使用者，提供统一的参数访问能力。
 * 它不关心路由分发或请求调度，仅负责当前请求作用域的数据封装与访问。
 *
 * <h2>线程模型</h2>
 * <p>
 * 本类实例与请求一一对应，生命周期与请求绑定。
 * 仅在当前请求的执行线程中使用，不应跨线程共享。
 * </p>
 *
 * @see HttpContext
 */
public final class FullHttpContext implements HttpContext {

	/** 请求消息：请求方式、请求头、请求地址与请求体内容 */
	private final HttpRequestMessage message;

	/** 当前请求关联的会话对象 */
	private final HttpSession session;

	/** 当前请求的连接会话 */
	private final ConnectSession connectSession;

	/** 当前请求的 Cookie 集合 */
	private final HttpCookies httpCookies;

	/** 装配期判定的缺省编码，经 {@link #defaultCharset()} 对外提供，请求体解码缺省时亦回落到它 */
	private final Charset defaultCharset;

	/** 请求上下文的挂载对象 */
	private final Attachment attachment = new DefaultAttachment();

	/**
	 * 构造完整的 HTTP 上下文，装配请求上下文的核心依赖。
	 * <p>请求消息（含请求体内容）由调用方（编排器）就位，本构造不再从请求对象取数。</p>
	 *
	 * @param message         请求消息（方式、头、地址与请求体内容），不能为 null
	 * @param httpSession     HTTP 会话对象
	 * @param cookies         当前请求的 Cookie 集合
	 * @param connectSession  当前连接会话（用于 WebSocket / SSE）
	 * @param defaultCharset  缺省编码（装配期由 HttpConfig 判定并随请求传递），不能为 null
	 * @throws NullPointerException message 或 defaultCharset 为 null
	 */
	public FullHttpContext(
			HttpRequestMessage message,
			HttpSession httpSession,
			HttpCookies cookies,
			ConnectSession connectSession,
			Charset defaultCharset
	) {
		this.message = Objects.requireNonNull(message, "message can not be null");
		this.session = httpSession;
		this.httpCookies = cookies;
		this.connectSession = connectSession;
		this.defaultCharset = Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpRequestMessage message() {
		return this.message;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ConnectSession getConnectSession() {
		return this.connectSession;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpSession httpSession() {
		return this.session;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpCookies cookies() {
		return this.httpCookies;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Charset defaultCharset() {
		return this.defaultCharset;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Attachment attachment() {
		return this.attachment;
	}
}
