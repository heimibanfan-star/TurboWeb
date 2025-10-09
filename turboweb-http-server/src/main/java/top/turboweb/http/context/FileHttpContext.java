package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.session.HttpSession;

import java.util.List;

/**
 * 抽象的文件操作的上下文
 */
public abstract class FileHttpContext extends CoreHttpContext {

	protected FileHttpContext(
			FullHttpRequest request,
			HttpSession httpSession,
			HttpCookieManager cookieManager,
			ConnectSession connectSession,
			JsonSerializer jsonSerializer
	) {
		super(request, httpSession, cookieManager, connectSession, jsonSerializer);
	}



	@Override
	public List<FileUpload> loadFiles(String fileName) {
		return httpContent.getFormFiles().get(fileName);
	}

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
