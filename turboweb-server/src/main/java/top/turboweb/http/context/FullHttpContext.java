package top.turboweb.http.context;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.context.attchment.DefaultAttachment;
import top.turboweb.http.context.content.HttpContent;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.InternalSseEmitter;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code FullHttpContext} 是 TurboWeb 框架中 {@link HttpContext} 的唯一实现。
 * <p>
 * 它封装原始请求、Session、Cookie、连接会话、请求内容（{@link HttpContent}）、
 * JSON 序列化器、缺省编码与挂载对象，并据此提供 SSE 创建、请求体参数封装
 * （表单/JSON/上传文件）与 bean 绑定服务。
 * <p>
 * 该类主要面向业务层使用者，提供统一的参数访问与数据绑定能力。
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

	/** 原始 Netty HTTP 请求对象 */
	private final FullHttpRequest request;

	/** 当前请求关联的会话对象 */
	private final HttpSession session;

	/** 当前请求的连接会话 */
	private final ConnectSession connectSession;

	/** 当前请求的 Cookie 集合 */
	private final HttpCookies httpCookies;

	/** 封装后的请求内容对象 */
	final HttpContent httpContent;

	/** JSON 序列化器 */
	private final JsonSerializer jsonSerializer;

	/** 装配期判定的缺省编码，经 {@link #defaultCharset()} 对外提供，请求体解码缺省时亦回落到它 */
	private final Charset defaultCharset;

	/** 请求上下文的挂载对象 */
	private final Attachment attachment = new DefaultAttachment();

	/**
	 * 构造完整的 HTTP 上下文，初始化请求上下文的核心依赖。
	 * <p>HEAD 请求无请求体，内容侧以空实例承载；其余请求按给定缺省编码构造 {@link HttpContent}。</p>
	 *
	 * @param request         当前请求对象
	 * @param httpSession     HTTP 会话对象
	 * @param cookies         当前请求的 Cookie 集合
	 * @param connectSession  当前连接会话（用于 WebSocket / SSE）
	 * @param jsonSerializer  JSON 序列化器
	 * @param defaultCharset  缺省编码（装配期由 HttpConfig 判定并随请求传递），不能为 null
	 * @throws NullPointerException defaultCharset 为 null
	 */
	public FullHttpContext(
			FullHttpRequest request,
			HttpSession httpSession,
			HttpCookies cookies,
			ConnectSession connectSession,
			JsonSerializer jsonSerializer,
			Charset defaultCharset
	) {
		this.request = request;
		this.session = httpSession;
		this.httpCookies = cookies;
		this.connectSession = connectSession;
		this.defaultCharset = Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
		HttpMethod method = request.method();
		if (HttpMethod.HEAD == method) {
			httpContent = HttpContent.empty();
		} else {
			httpContent = new HttpContent(request, defaultCharset);
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
	 */
	@Override
	public <T> T bindBean(Map<String, List<String>> paramsMap, Class<T> beanType) {
		return jsonSerializer.mapToBean(collapseParamValues(paramsMap), beanType);
	}

	/**
	 * 将 {@code Map<String, List<String>>} 转换为 {@code Map<String, Object>}:
	 * 值列表仅一个元素时取该元素(可为 null),多个元素保留列表。
	 *
	 * @param oldMap 原始多值集合
	 * @return 折叠后的键值集合
	 */
	private static Map<String, Object> collapseParamValues(Map<String, List<String>> oldMap) {
		Map<String, Object> newMap = new HashMap<>();
		oldMap.forEach((key, value) -> {
			if (value.size() == 1) {
				newMap.put(key, value.getFirst());
			} else {
				newMap.put(key, value);
			}
		});
		return newMap;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T loadForm(Class<T> beanType) {
		return bindBean(httpContent.getFormParams(), beanType);
	}

	/**
	 * {@inheritDoc}
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
	 * {@inheritDoc}
	 */
	@Override
	public List<FileUpload> loadFiles(String fileName) {
		List<FileUpload> fileUploads = httpContent.getFormFiles().get(fileName);
		// 字段不存在归一为空集合
		return fileUploads == null ? List.of() : fileUploads;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FileUpload loadFile(String fileName) {
		List<FileUpload> fileUploads = loadFiles(fileName);
		return fileUploads.isEmpty() ? null : fileUploads.getFirst();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void release() {
		httpContent.release();
	}
}
