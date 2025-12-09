package top.turboweb.http.context;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.respmeta.DefaultResponseMeta;
import top.turboweb.http.context.respmeta.ResponseMeta;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.context.content.HttpContent;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.response.InternalSseEmitter;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.session.HttpSession;

import java.util.function.Consumer;


/**
 * {@code CoreHttpContext} 是 {@link HttpContext} 的抽象基础实现，
 * 定义了 TurboWeb 框架中 HTTP 请求上下文的核心行为与通用逻辑。
 * <p>
 * 该类封装了请求对象、Session、Cookie 管理、SSE 事件流创建、
 * 响应元信息（{@link ResponseMeta}）以及请求内容解析等功能。
 * <br>
 * 具体框架实现可通过继承本类并扩展自定义上下文逻辑。
 * </p>
 *
 * <h2>职责说明</h2>
 * <ul>
 *   <li>负责维护一次 HTTP 请求的上下文状态。</li>
 *   <li>提供基础的 SSE 创建、Session 管理、Cookie 管理等能力。</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <p>
 * 本类实例与请求一一对应，生命周期与请求绑定。
 * 每个 {@code CoreHttpContext} 仅在当前请求的执行线程中使用，不应跨线程共享。
 * </p>
 *
 * @see HttpContext
 * @see ResponseMeta
 * @see SseResponse
 * @see InternalSseEmitter
 */
public abstract class CoreHttpContext implements HttpContext{

	/** 原始 Netty HTTP 请求对象 */
	protected final FullHttpRequest request;

	/** 当前请求关联的会话对象 */
	protected final HttpSession session;

	/** 当前请求的连接会话 */
	protected final ConnectSession connectSession;

	/** Cookie 管理器 */
	protected final HttpCookieManager httpCookieManager;

	/** 封装后的请求内容对象 */
	protected final HttpContent httpContent;

	/** JSON 序列化器 */
	protected final JsonSerializer jsonSerializer;

	/** 响应元信息对象（状态码与内容类型） */
	private final ResponseMeta responseMeta = new DefaultResponseMeta();

	/**
	 * 构造方法，用于初始化请求上下文的核心依赖。
	 *
	 * @param request         当前请求对象
	 * @param httpSession     HTTP 会话对象
	 * @param cookieManager   Cookie 管理器
	 * @param connectSession  当前连接会话
	 * @param jsonSerializer  JSON 序列化器
	 */
	protected CoreHttpContext(
			FullHttpRequest request,
			HttpSession httpSession,
			HttpCookieManager cookieManager,
			ConnectSession connectSession,
			JsonSerializer jsonSerializer
	) {
		this.request = request;
		this.session = httpSession;
		this.httpCookieManager = cookieManager;
		this.connectSession = connectSession;
		HttpMethod method = request.method();
		if (HttpMethod.GET == method || HttpMethod.HEAD == method) {
			httpContent = HttpContent.empty();
		} else {
			httpContent = new HttpContent(request);
		}
		this.jsonSerializer = jsonSerializer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FullHttpRequest getRequest() {
		return this.request;
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
	public HttpCookieManager cookie() {
		return this.httpCookieManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResponseMeta getResponseMeta() {
		return responseMeta;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void responseMeta(Consumer<ResponseMeta> consumer) {
		consumer.accept(responseMeta);
	}
}
