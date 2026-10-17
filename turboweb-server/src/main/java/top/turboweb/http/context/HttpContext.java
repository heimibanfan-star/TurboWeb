package top.turboweb.http.context;

import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.http.context.attchment.Attachment;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.rest.HttpCookies;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.rest.Http;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * {@code HttpContext} 表示 TurboWeb 框架中一次 HTTP 请求的上下文环境。
 * <p>
 * 它封装了请求信息、连接会话、Session 管理、Cookie 管理、请求体内容
 * （表单字段、JSON 字符串与上传文件的封装权在解析器产出的内容对象侧，读取通道见 {@link Http.Content}
 * 的能力子契约 {@link FormContent} 与 {@link JsonContent}）。
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
 *     Http.Message message = ctx.message();
 *     String method = message.method();
 *     String path = message.url().url(true);
 *
 *     // 获取Session属性
 *     HttpSession session = ctx.httpSession();
 *     session.setAttr("user", "Alice");
 *
 *     // 读表单字段：参数取数经内容的 FormContent 能力契约；表单到对象的绑定由路由侧 @BeanForm 参数绑定器完成
 *     HttpContext.FormContent form = (HttpContext.FormContent) ctx.content();
 *     String keyword = form.formParams().get("keyword").getFirst();
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
 */
public interface HttpContext {

	/**
	 * 可关闭的请求体内容：组合 {@link Http.Content} 与 {@link AutoCloseable} 的公共子类型，
	 * 持有方在请求处理结束后经 {@link AutoCloseable#close()} 统一释放。
	 * 实现类仅在持有需归还的资源（如解码器、直接内存、磁盘临时文件）时选用本接口，
	 * 无此类资源的实现只实现 {@link Http.Content}，不背负关闭义务。
	 */
	interface AutoCloseContent extends Http.Content, AutoCloseable {
	}

	/**
	 * 表单能力契约：可提供表单普通字段与上传文件归档的 {@link Http.Content} 子类型，
	 * 由承接 {@code application/x-www-form-urlencoded} 与 {@code multipart/form-data}
	 * 的解析器产出的内容实现。字段映射键为参数名、值为按出现顺序的取值列表；
	 * 文件归档键为表单字段名，缺名不建键，空值归一由取用方决定。
	 */
	interface FormContent extends Http.Content {

		/**
		 * 获取表单普通字段集合。
		 *
		 * @return key 为参数名、value 为值列表的映射
		 */
		Map<String, List<String>> formParams();

		/**
		 * 获取上传文件集合。
		 *
		 * @return key 为文件字段名、value 为该字段文件列表的映射
		 */
		Map<String, List<FileUpload>> formFiles();
	}

	/**
	 * JSON 能力契约：可提供 JSON 请求体字符串的 {@link Http.Content} 子类型，
	 * 由承接 {@code application/json} 的解析器产出的内容实现；
	 * 字符串为装配期解码规整后的 JSON 对象文本。
	 */
	interface JsonContent extends Http.Content {

		/**
		 * 获取 JSON 请求体字符串。
		 *
		 * @return 规整后的字符串本身
		 */
		String jsonString();
	}

	/**
	 * 获取当前请求的消息：请求方式、请求头与请求地址。
	 * <p>由编排器在装配上下文时从请求对象转化而来，本接口不暴露请求对象本身。</p>
	 *
	 * @return 请求消息，永不为 null
	 */
	Http.Message message();

	/**
	 * 获取当前请求的请求体内容。
	 * <p>由编排器按请求头声明的内容类型分派解析器产出；内容类型未声明或无对应解析器时为
	 * {@link HttpUnknowContent}，无请求体的请求（如 HEAD）同样以此承载。</p>
	 * <p>返回对象中持有资源的实现（{@link AutoCloseContent}）由本上下文统一管理，
	 * 在 {@link #release()} 时关闭，使用者无须也不应提前关闭。</p>
	 *
	 * @return 请求体内容，永不为 null
	 */
	Http.Content content();

	/**
	 * 获取连接会话。
	 *
	 * @return 当前请求关联的 {@link ConnectSession} 对象。
	 */
	ConnectSession getConnectSession();

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
}
