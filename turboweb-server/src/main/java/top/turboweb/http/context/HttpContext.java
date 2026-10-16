package top.turboweb.http.context;

import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.constant.ContentType;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code HttpContext} 表示 TurboWeb 框架中一次 HTTP 请求的上下文环境。
 * <p>
 * 它封装了请求信息、连接会话、Session 管理、Cookie 管理、请求体参数访问
 * （表单/JSON 封装与上传文件读取，见本接口末段方法）、
 * 以及用于创建 SSE（Server-Sent Events）流的相关方法。
 * <br>
 * 每个请求都拥有独立的 {@code HttpContext} 实例，生命周期与请求保持一致。
 * </p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>职责单一：负责维护请求级上下文状态。</li>
 *   <li>框架无侵入：不限制用户在业务方法中的调用顺序。</li>
 *   <li>面向接口：唯一实现由 {@link FullHttpContext} 提供。</li>
 *   <li>体侧边界：本接口只承载请求体数据的读取；查询参数与路径参数是 URI 与路由匹配的产物，
 *       由路由装配解码后随处理器调用传递（承载对象 {@code RequestParameter}），
 *       经参数绑定器按注解取数（{@code @QueryParam}/{@code @BeanQuery}/{@code @PathParam}），
 *       不经请求上下文中转。</li>
 * </ul>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * void handle(HttpContext ctx) {
 *     // 获取请求消息：方式、请求头与请求地址
 *     HttpContext.Message message = ctx.message();
 *     String method = message.method();
 *     String path = message.url().url(true);
 *
 *     // 获取Session属性
 *     HttpSession session = ctx.httpSession();
 *     session.setAttr("user", "Alice");
 *
 *     // 表单绑定为对象
 *     SearchRequest query = ctx.loadForm(SearchRequest.class);
 *
 *     // 释放上下文资源
 *     ctx.release();
 * }
 * }</pre>
 *
 * <p><b>线程安全性：</b> 本接口的实现类通常不是线程安全的，
 * 每个 HTTP 请求应独立持有上下文实例。</p>
 *
 * @see FullHttpContext
 * @see SseResponse
 * @see SseEmitter
 */
public interface HttpContext {

	/**
	 * 请求体内容的读取契约：声明体侧内容类型，不携带关闭义务。
	 * 无显式释放需求的实现只实现本接口即可。
	 */
	interface Content {

		ContentType contentType();
	}

	/**
	 * 可关闭的请求体内容：组合 {@link Content} 与 {@link AutoCloseable} 的公共子类型，
	 * 持有方在请求处理结束后经 {@link AutoCloseable#close()} 统一释放。
	 * 实现类仅在持有需归还的资源（如解码器、直接内存、磁盘临时文件）时选用本接口，
	 * 无此类资源的实现只实现 {@link Content}，不背负关闭义务。
	 */
	interface AutoCloseContent extends Content, AutoCloseable {
	}

	/**
	 * http请求消息：请求方式、请求头与请求地址的读取入口。
	 */
	interface Message {

		/**
		 * 获取请求方式，取请求行的方法字面量。
		 *
		 * @return 请求方式（标准方法为大写，如 {@code GET}、{@code POST}）
		 */
		String method();

		/**
		 * 获取请求头只读视图。
		 *
		 * @return 请求头
		 */
		Headers headers();

		/**
		 * 获取请求地址。
		 *
		 * @return 请求地址
		 */
		URL url();
	}

	/**
	 * 请求地址：承载请求行的原始 URI（含路径，可带查询段与片段段），
	 * 提供地址取用与查询参数解码两个通道。
	 */
	interface URL {

		/**
		 * 获取请求地址。
		 *
		 * @param pathOnly 为 {@code true} 时仅获取地址本体：自第一个 {@code ?} 或 {@code #}
		 *                 处截断，不携带查询参数与片段；为 {@code false} 时返回原始地址
		 * @return 请求地址字符串
		 */
		String url(boolean pathOnly);

		/**
		 * 获取查询参数：解析地址查询段，键按首现顺序排列，同名参数取值按出现顺序保留；
		 * 无等号段（如 {@code ?flag}）取值为 {@code null}；百分号转义按装配期缺省字符集
		 * 解码，转义畸形时抛出 {@link IllegalArgumentException}。
		 *
		 * @return 参数名到值列表的不可变映射；无查询段时返回空 Map
		 */
		Map<String, List<String>> queryParams();
	}

	/**
	 * 请求头：只读视图，头名查询大小写不敏感。泛型访问器参照 Netty HttpHeaders 的
	 * 只读口径（取首值、取全部值、包含判断、名称枚举、条目计数）；常用请求头便捷
	 * 方法返回原始头值（charset 等附加参数段原样保留），头未发送时返回 {@code null}，
	 * 仅 {@link #contentLength()} 未发送或取值无效时记 0。
	 */
	interface Headers {

		/**
		 * 获取指定头名的首个取值。
		 *
		 * @param name 头名，大小写不敏感，不能为空
		 * @return 首个取值；头不存在时返回 {@code null}
		 */
		String get(String name);

		/**
		 * 获取指定头名的全部取值，按出现顺序。
		 *
		 * @param name 头名，大小写不敏感，不能为空
		 * @return 取值列表；头不存在时返回空列表
		 */
		List<String> getAll(String name);

		/**
		 * 判断头是否存在。
		 *
		 * @param name 头名，大小写不敏感，不能为空
		 * @return 存在返回 true
		 */
		boolean contains(String name);

		/**
		 * 获取全部头名，按发送原文并去重。
		 *
		 * @return 头名集合
		 */
		Set<String> names();

		/**
		 * 获取头条目数，同名多值按条目分别计数。
		 *
		 * @return 条目数
		 */
		int size();

		/**
		 * 判断是否无任何请求头。
		 *
		 * @return 无头返回 true
		 */
		boolean isEmpty();

		/** Content-Type 头原始值 */
		default String contentType() {
			return get("Content-Type");
		}

		/**
		 * Content-Length 头取值。头缺失、非数值或为负时记 0。
		 *
		 * @return 请求体声明长度
		 */
		default long contentLength() {
			String value = get("Content-Length");
			if (value == null) {
				return 0L;
			}
			try {
				return Math.max(Long.parseLong(value.trim()), 0L);
			} catch (NumberFormatException e) {
				return 0L;
			}
		}

		/** Host 头 */
		default String host() {
			return get("Host");
		}

		/** Connection 头 */
		default String connection() {
			return get("Connection");
		}

		/** Date 头 */
		default String date() {
			return get("Date");
		}

		/** Expect 头 */
		default String expect() {
			return get("Expect");
		}

		/** Accept 头 */
		default String accept() {
			return get("Accept");
		}

		/** Accept-Charset 头 */
		default String acceptCharset() {
			return get("Accept-Charset");
		}

		/** Accept-Encoding 头 */
		default String acceptEncoding() {
			return get("Accept-Encoding");
		}

		/** Accept-Language 头 */
		default String acceptLanguage() {
			return get("Accept-Language");
		}

		/** Authorization 头 */
		default String authorization() {
			return get("Authorization");
		}

		/** Cache-Control 头 */
		default String cacheControl() {
			return get("Cache-Control");
		}

		/**
		 * Cookie 头原始值；结构化的 Cookie 读写走 {@link HttpCookies}。
		 *
		 * @return 头原始值
		 */
		default String cookie() {
			return get("Cookie");
		}

		/** Origin 头 */
		default String origin() {
			return get("Origin");
		}

		/** Referer 头 */
		default String referer() {
			return get("Referer");
		}

		/** User-Agent 头 */
		default String userAgent() {
			return get("User-Agent");
		}
	}

	/**
	 * 获取当前请求的消息：请求方式、请求头与请求地址。
	 * <p>由编排器在装配上下文时从请求对象转化而来，本接口不暴露请求对象本身。</p>
	 *
	 * @return 请求消息，永不为 null
	 */
	Message message();

	/**
	 * 获取当前请求的请求体内容。
	 * <p>由编排器按请求头声明的内容类型分派解析器产出；内容类型未声明或无对应解析器时为
	 * {@link HttpUnknowContent}，无请求体的请求（如 HEAD）同样以此承载。</p>
	 * <p>返回对象中持有资源的实现（{@link AutoCloseContent}）由本上下文统一管理，
	 * 在 {@link #release()} 时关闭，使用者无须也不应提前关闭。</p>
	 *
	 * @return 请求体内容，永不为 null
	 */
	Content content();

	/**
	 * 获取连接会话。
	 *
	 * @return 当前请求关联的 {@link ConnectSession} 对象。
	 */
	ConnectSession getConnectSession();

	/**
	 * 创建一个新的 SSE（Server-Sent Events）响应实例。
	 *
	 * @return 用于构建 SSE 响应的 {@link SseResponse}。
	 */
	SseResponse createSseResponse();

	/**
	 * 获取当前 HTTP 会话对象。
	 *
	 * @return {@link HttpSession} 对象，用于存储和读取会话数据。
	 */
	HttpSession httpSession();

	/**
	 * 获取当前请求的 Cookie 集合。
	 *
	 * @return {@link HttpCookies}，用于操作请求和响应中的 Cookie。
	 */
	HttpCookies cookies();

	/**
	 * 获取装配期判定的缺省字符集（{@link top.turboweb.http.server.config.HttpConfig} 配置值），
	 * 不关注请求头声明的编码。
	 * <p>路径参数解码、响应文本编码、响应内容类型补码与 Cookie 值编码统一取本值；
	 * 请求体解码另行按请求头 Content-Type 优先、缺省回落到本值判定。</p>
	 *
	 * @return 缺省字符集，永不为 null
	 */
	Charset defaultCharset();

	/**
	 * 创建默认配置的 SSE 事件发射器。
	 *
	 * @return {@link SseEmitter} 对象，用于服务端事件推送。
	 */
	SseEmitter createSseEmitter();

	/**
	 * 创建指定缓存大小的 SSE 事件发射器。
	 *
	 * @param maxMessageCache SSE 消息缓存的最大容量
	 * @return {@link SseEmitter} 对象，用于服务端事件推送。
	 */
	SseEmitter createSseEmitter(int maxMessageCache);

	/**
	 * 释放当前上下文持有的资源。
	 * <p>关闭 {@link #content()} 中持有资源的实现（表单解码器、上传文件的直接内存与磁盘临时文件）。</p>
	 * <p>本方法不释放请求对象本身，请求的释放由持有请求的编排环节负责。</p>
	 */
	void release();

	/**
	 * 获取上下文中的挂载对象。
	 * <p>允许用户在控制器方法中挂载对象，并在后续处理中访问。</p>
	 *
	 * @return {@link Attachment} 对象，用于挂载对象。
	 */
	Attachment attachment();

	/**
	 * 将多值参数映射绑定为 bean:单值列表折叠为字符串、多值列表保留(无值段为 null 原样传递),
	 * 经 JSON 序列化器完成属性映射。本方法不读取请求数据,映射数据由调用方就位。
	 *
	 * @param paramsMap 参数名到值列表的映射,可为空
	 * @param beanType  目标 bean 类型,不能为 null
	 * @return 绑定后的 bean;未知属性按序列化器忽略未知字段的口径跳过
	 */
	<T> T bindBean(Map<String, List<String>> paramsMap, Class<T> beanType);

	/**
	 * 加载并反序列化表单参数,封装成对象。
	 *
	 * @param beanType 对象类型
	 * @return 对象
	 */
	<T> T loadForm(Class<T> beanType);

	/**
	 * 反序列化 JSON 请求体为对象。
	 *
	 * @param beanType 对象类型
	 * @return 对象
	 * @throws top.turboweb.http.exception.RequestParseException 请求体 Content-Type 非 JSON 或内容无效时抛出
	 */
	<T> T loadJson(Class<T> beanType);

	/**
	 * 根据表单字段名获取对应的所有上传文件。
	 *
	 * @param fileName 表单中文件字段名
	 * @return 上传文件列表；若字段不存在则返回空集合
	 */
	List<FileUpload> loadFiles(String fileName);

	/**
	 * 根据表单字段名获取对应的第一个上传文件。
	 * <p>
	 * 若字段存在多个文件，则仅返回第一个；
	 * 若字段不存在或文件列表为空，则返回 {@code null}。
	 *
	 * @param fileName 表单中文件字段名
	 * @return 第一个上传文件或 {@code null}
	 */
	FileUpload loadFile(String fileName);
}
