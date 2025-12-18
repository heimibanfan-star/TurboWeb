package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.context.respmeta.ResponseMeta;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;

/**
 * {@code HttpContext} 表示 TurboWeb 框架中一次 HTTP 请求的上下文环境。
 * <p>
 * 它封装了请求信息、连接会话、Session 管理、Cookie 管理、响应元信息（{@link ResponseMeta}）、
 * 以及用于创建 SSE（Server-Sent Events）流的相关方法。
 * <br>
 * 每个请求都拥有独立的 {@code HttpContext} 实例，生命周期与请求保持一致。
 * </p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>职责单一：负责维护请求级上下文状态。</li>
 *   <li>框架无侵入：不限制用户在业务方法中的调用顺序。</li>
 *   <li>面向接口：实际行为由 {@link CoreHttpContext} 提供核心实现。</li>
 * </ul>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * void handle(HttpContext ctx) {
 *     // 获取请求
 *     FullHttpRequest req = ctx.getRequest();
 *
 *     // 获取Session属性
 *     HttpSession session = ctx.httpSession();
 *     session.setAttr("user", "Alice");
 *
 *     // 释放上下文资源
 *     ctx.release();
 * }
 * }</pre>
 *
 * <p><b>线程安全性：</b> 本接口的实现类通常不是线程安全的，
 * 每个 HTTP 请求应独立持有上下文实例。</p>
 *
 * @see CoreHttpContext
 * @see SseResponse
 * @see SseEmitter
 */
public interface HttpContext extends ParamBinder {

	/**
	 * 获取当前请求对象。
	 *
	 * @return Netty 的 {@link FullHttpRequest} 对象，包含请求头、URI、方法及请求体。
	 */
	FullHttpRequest getRequest();

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
	 * 获取 Cookie 管理器。
	 *
	 * @return {@link HttpCookieManager}，用于操作请求和响应中的 Cookie。
	 */
	HttpCookieManager cookie();

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
	 * <p>例如释放 {@link FullHttpRequest} 的引用，回收缓冲区等。</p>
	 */
	void release();

	/**
	 * 获取上下文中的挂载对象。
	 * <p>允许用户在控制器方法中挂载对象，并在后续处理中访问。</p>
	 *
	 * @return {@link Attachment} 对象，用于挂载对象。
	 */
	Attachment attachment();
}
