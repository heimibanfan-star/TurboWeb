package top.turboweb.http.context;

import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.cookie.HttpCookies;
import top.turboweb.http.session.HttpSession;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.rest.core.HttpRequestMessage;

import java.nio.charset.Charset;

/**
 * {@code HttpContext} 表示 TurboWeb 框架中一次 HTTP 请求的上下文环境。
 * <p>
 * 它封装了请求消息（方式、请求头、请求地址与请求体内容）、连接会话、Session 管理、
 * Cookie 管理、缺省编码与挂载对象。
 * 请求体内容经 {@link HttpRequestMessage#content()} 读取（读取契约见 {@link HttpContent}）；
 * 表单字段与上传文件、JSON 体字符串由内置解析器产出的具体内容类承载
 * （分别为 {@link HttpFormContent} 与 {@link HttpJsonContent}），读取侧认其具体类转型取数。
 * 本接口不单独暴露内容访问器，也不承担内容的关闭与释放——
 * 请求体资源由创建方（请求编排器）在请求结束时统一释放。
 * <br>
 * 每个请求都拥有独立的 {@code HttpContext} 实例，生命周期与请求保持一致。
 * </p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>职责单一：负责维护请求级上下文状态。</li>
 *   <li>框架无侵入：不限制用户在业务方法中的调用顺序。</li>
 *   <li>面向接口：唯一实现由 {@link FullHttpContext} 提供。</li>
 *   <li>体侧边界：本接口只承载请求体数据的读取入口；查询参数与路径参数是 URI 与路由匹配的产物，
 *       由路由装配解码后随处理器调用传递（承载对象 {@code RequestParameter}），
 *       经参数绑定器按注解取数（{@code @QueryParam}/{@code @BeanQuery}/{@code @PathParam}），
 *       不经请求上下文中转。</li>
 * </ul>
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * void handle(HttpContext ctx) {
 *     // 获取请求消息：方式、请求头、请求地址与请求体内容
 *     HttpRequestMessage message = ctx.message();
 *     HttpRequestMethod method = message.method();
 *     String path = message.url().url(true);
 *
 *     // 获取Session属性
 *     HttpSession session = ctx.httpSession();
 *     session.setAttr("user", "Alice");
 *
 *     // 读表单字段：参数取数经消息内容转型内置表单内容类；表单到对象的绑定由路由侧 @BeanForm 参数绑定器完成
 *     HttpFormContent form = (HttpFormContent) message.content();
 *     String keyword = form.formParams().get("keyword").getFirst();
 * }
 * }</pre>
 *
 * <p><b>线程安全性：</b> 本接口的实现类通常不是线程安全的，
 * 每个 HTTP 请求应独立持有上下文实例。</p>
 *
 * @see FullHttpContext
 */
public interface HttpContext {

	/**
	 * 获取当前请求的消息：请求方式、请求头、请求地址与请求体内容。
	 * <p>由编排器在装配上下文时从请求对象转化而来，本接口不暴露请求对象本身。</p>
	 *
	 * @return 请求消息，永不为 null
	 */
	HttpRequestMessage message();

	/**
	 * 获取连接会话。
	 *
	 * @return 当前请求关联的 {@link HttpConnection} 对象。
	 */
	HttpConnection getConnectSession();

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
	 * 获取上下文中的挂载对象。
	 * <p>允许用户在控制器方法中挂载对象，并在后续处理中访问。</p>
	 *
	 * @return {@link Attachment} 对象，用于挂载对象。
	 */
	Attachment attachment();
}
