package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.util.List;

/**
 * 文件上传场景下的 HTTP 上下文抽象类。
 * <p>
 * 该类在 {@link CoreHttpContext} 的基础上扩展实了对表单文件数据的访问能力，
 * 封装了对 {@code multipart/form-data} 请求中上传文件的读取逻辑。
 * <p>
 * 主要用于：
 * <ul>
 *   <li>简化文件上传表单的文件读取。</li>
 *   <li>提供单文件和多文件两种访问方式。</li>
 *   <li>为具体业务上下文（如 {@code DefaultFileHttpContext}）提供基础实现。</li>
 * </ul>
 * <p>
 * 一般情况下无需关心该类，在HttpContext可以直接调用该类的方法
 */
public abstract class FileHttpContext extends CoreHttpContext {

	/**
	 * 构造文件 HTTP 上下文。
	 *
	 * @param request        HTTP 请求对象
	 * @param httpSession    会话对象
	 * @param cookies  当前请求的 Cookie 集合
	 * @param connectSession 连接会话对象，用于 WebSocket/SSE 等场景
	 * @param jsonSerializer JSON 序列化器
	 * @param defaultCharset 缺省编码（装配期判定并随请求传递），不能为 null
	 */
	protected FileHttpContext(
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
	 * 根据表单字段名获取对应的所有上传文件。
	 *
	 * @param fileName 表单中文件字段名
	 * @return 上传文件列表；若字段不存在则返回空集合
	 */
	@Override
	public List<FileUpload> loadFiles(String fileName) {
		return httpContent.getFormFiles().get(fileName);
	}

	/**
	 * 根据表单字段名获取对应的第一个上传文件。
	 * <p>
	 * 若字段存在多个文件，则仅返回第一个；
	 * 若字段不存在或文件列表为空，则返回 {@code null}。
	 *
	 * @param fileName 表单中文件字段名
	 * @return 第一个上传文件或 {@code null}
	 */
	@Override
	public FileUpload loadFile(String fileName) {
		List<FileUpload> fileUploads = httpContent.getFormFiles().get(fileName);
		if (fileUploads == null || fileUploads.isEmpty()) {
			return null;
		} else {
			return fileUploads.getFirst();
		}
	}
}
