package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import top.turboweb.commons.exception.TurboFileException;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;

/**
 * 抽象的文件响应对象，用于将文件以 HTTP 响应形式返回客户端。
 * <p>
 * 提供文件校验、响应头初始化和文件名编码等基础功能。
 * 支持通过文件名或 {@link File} 对象构造响应。
 * </p>
 */
public abstract class AbstractFileResponse extends DefaultHttpResponse {

	/**
	 * 使用 {@link File} 构造文件响应
	 *
	 * @param status          HTTP 响应状态
	 * @param file            文件对象
	 * @param filenameCharset 文件名编码字符集
	 */
	protected AbstractFileResponse(HttpResponseStatus status, File file, Charset filenameCharset) {
		super(HttpVersion.HTTP_1_1, status);
		init(file, filenameCharset);
	}

	/**
	 * 使用文件名构造文件响应
	 *
	 * @param status          HTTP 响应状态
	 * @param filename        文件名
	 * @param filenameCharset 文件名编码字符集
	 */
	protected AbstractFileResponse(HttpResponseStatus status, String filename, Charset filenameCharset) {
		super(HttpVersion.HTTP_1_1, status);
		initHeaders(filename, filenameCharset);
	}

	/**
	 * 初始化文件响应
	 *
	 * @param file            文件对象
	 * @param filenameCharset 文件名编码字符集
	 */
	private void init(File file, Charset filenameCharset) {
		fileVerify(file);
		initHeaders(file.getName(), filenameCharset);
	}

	/**
	 * 校验文件有效性
	 *
	 * @param file 文件对象
	 * @throws TurboFileException 如果文件不存在、为文件夹或不可读
	 */
	private void fileVerify(File file) {
		if (!file.exists()) {
			throw new TurboFileException("文件不存在," + file.getAbsolutePath());
		}
		if (file.isDirectory()) {
			throw new TurboFileException("文件是文件夹," + file.getAbsolutePath());
		}
		if (!file.canRead()) {
			throw new TurboFileException("文件不可读," + file.getAbsolutePath());
		}
	}

	/**
	 * 初始化文件响应的 HTTP 头
	 *
	 * @param filename 文件名
	 * @param charset  文件名编码字符集
	 */
	private void initHeaders(String filename, Charset charset) {
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
		this.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeFileName(filename, charset) + "\"");
		this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
	}

	/**
	 * 对文件名进行 URL 编码，确保中文和特殊字符正确显示
	 *
	 * @param fileName        文件名
	 * @param filenameCharset 编码字符集
	 * @return 编码后的文件名
	 */
	private String encodeFileName(String fileName, Charset filenameCharset) {
		// 使用 UTF-8 编码，并对中文进行转码处理
		return URLEncoder.encode(fileName, filenameCharset).replace("+", "%20");
	}

	/**
	 * 设置文件的 Content-Type
	 *
	 * @param contentType 文件类型
	 */
	public void setContentType(ContentType contentType) {
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());
	}

}
