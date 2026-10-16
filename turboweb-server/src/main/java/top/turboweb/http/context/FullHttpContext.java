package top.turboweb.http.context;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.context.attchment.DefaultAttachment;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.InternalSseEmitter;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * {@code FullHttpContext} 是 TurboWeb 框架中 {@link HttpContext} 的唯一实现。
 * <p>
 * 它封装请求消息（{@link HttpContext.Message}）、请求体内容（{@link HttpContext.Content}）、
 * Session、Cookie、连接会话、JSON 序列化器、缺省编码与挂载对象，并据此提供 SSE 创建；
 * 请求体参数的封装与读取经内容对象的能力子契约（{@link HttpContext.FormContent}、
 * {@link HttpContext.JsonContent}）完成，本类不按具体实现类分派。
 * <p>
 * 本类不持有 Netty 的请求对象：请求的方式、头与地址经 {@link HttpContext.Message} 读取，
 * 请求体经内容解析器产出的 {@link HttpContext.Content} 读取，两者均由编排器在装配上下文时就位。
 * 请求体内容为 {@link HttpContext.AutoCloseContent} 时，其资源在 {@link #release()} 时关闭。
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
 * @see SseResponse
 * @see SseEmitter
 */
public final class FullHttpContext implements HttpContext {

	/** 内容关闭失败的记录器，走 netty 标准日志通道 */
	private static final InternalLogger log = InternalLoggerFactory.getInstance(FullHttpContext.class);

	/** 请求消息：请求方式、请求头与请求地址 */
	private final HttpContext.Message message;

	/** 请求体内容：由内容解析器产出，持有资源的实现在释放时关闭 */
	private final HttpContext.Content content;

	/** 当前请求关联的会话对象 */
	private final HttpSession session;

	/** 当前请求的连接会话 */
	private final ConnectSession connectSession;

	/** 当前请求的 Cookie 集合 */
	private final HttpCookies httpCookies;

	/** JSON 序列化器，创建 SSE 响应时注入响应对象 */
	private final JsonSerializer jsonSerializer;

	/** 装配期判定的缺省编码，经 {@link #defaultCharset()} 对外提供，请求体解码缺省时亦回落到它 */
	private final Charset defaultCharset;

	/** 请求上下文的挂载对象 */
	private final Attachment attachment = new DefaultAttachment();

	/**
	 * 构造完整的 HTTP 上下文，装配请求上下文的核心依赖。
	 * <p>请求消息与请求体内容都由调用方（编排器）就位，本构造不再从请求对象取数。</p>
	 *
	 * @param message         请求消息（方式、头与地址），不能为 null
	 * @param content         请求体内容，不能为 null
	 * @param httpSession     HTTP 会话对象
	 * @param cookies         当前请求的 Cookie 集合
	 * @param connectSession  当前连接会话（用于 WebSocket / SSE）
	 * @param jsonSerializer  JSON 序列化器（供 SSE 创建通道注入）
	 * @param defaultCharset  缺省编码（装配期由 HttpConfig 判定并随请求传递），不能为 null
	 * @throws NullPointerException message、content 或 defaultCharset 为 null
	 */
	public FullHttpContext(
			HttpContext.Message message,
			HttpContext.Content content,
			HttpSession httpSession,
			HttpCookies cookies,
			ConnectSession connectSession,
			JsonSerializer jsonSerializer,
			Charset defaultCharset
	) {
		this.message = Objects.requireNonNull(message, "message can not be null");
		this.content = Objects.requireNonNull(content, "content can not be null");
		this.session = httpSession;
		this.httpCookies = cookies;
		this.connectSession = connectSession;
		this.defaultCharset = Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
		this.jsonSerializer = jsonSerializer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpContext.Message message() {
		return this.message;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public HttpContext.Content content() {
		return this.content;
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
	public SseResponse createSseResponse() {
		return new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(), connectSession, jsonSerializer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SseEmitter createSseEmitter() {
		return new InternalSseEmitter(connectSession, 32);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SseEmitter createSseEmitter(int maxMessageCache) {
		return new InternalSseEmitter(connectSession, maxMessageCache);
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

	/**
	 * {@inheritDoc}
	 * <p>请求体内容为 {@link AutoCloseContent} 时关闭它，释放解码器持有的直接内存与磁盘临时文件；
	 * 关闭抛出的异常只记录不外抛，不影响请求其余环节的收尾。内容不持有资源时为空操作，
	 * 重复调用无副作用。</p>
	 */
	@Override
	public void release() {
		if (content instanceof AutoCloseContent autoCloseContent) {
			try {
				autoCloseContent.close();
			} catch (Exception e) {
				log.error("Failed to close http content", e);
			}
		}
	}
}
