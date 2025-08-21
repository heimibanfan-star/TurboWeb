package top.turboweb.http.context;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.apache.commons.io.FileUtils;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.exception.TurboResponseRepeatWriteException;
import top.turboweb.http.session.HttpSession;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 抽象的文件操作的上下文
 */
public abstract class FileHttpContext extends CoreHttpContext {

	protected FileHttpContext(HttpInfoRequest request, HttpSession httpSession, HttpCookieManager cookieManager, ConnectSession connectSession) {
		super(request, httpSession, cookieManager, connectSession);
	}



	@Override
	public List<FileUpload> loadFiles(String fileName) {
		return request.getContent().getFormFiles().get(fileName);
	}

	@Override
	public FileUpload loadFile(String fileName) {
		List<FileUpload> fileUploads = request.getContent().getFormFiles().get(fileName);
		if (fileUploads == null || fileUploads.isEmpty()) {
			return null;
		} else {
			return fileUploads.getFirst();
		}
	}
}
