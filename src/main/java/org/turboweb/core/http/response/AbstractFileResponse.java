package org.turboweb.core.http.response;

import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import org.turboweb.exception.TurboFileException;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;

/**
 * 抽象的文件响应对象
 */
public abstract class AbstractFileResponse extends DefaultHttpResponse {

	protected AbstractFileResponse(HttpVersion version, HttpResponseStatus status, File file, Charset filenameCharset) {
		super(version, status);
		init(file, filenameCharset);
	}

	private void init(File file, Charset filenameCharset) {
		fileVerify(file);
		initHeaders(file, filenameCharset);
	}

	/**
	 * 文件校验
	 *
	 * @param file 文件
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
	 * 初始化响应头
	 *
	 * @param file 文件
	 */
	private void initHeaders(File file, Charset charset) {
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
		this.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeFileName(file.getName(), charset) + "\"");
		this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
	}

	private String encodeFileName(String fileName, Charset filenameCharset) {
		// 使用 UTF-8 编码，并对中文进行转码处理
		return URLEncoder.encode(fileName, filenameCharset).replace("+", "%20");
	}

	/**
	 * 设置文件类型
	 *
	 * @param contentType 文件类型
	 */
	public void setContentType(ContentType contentType) {
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());
	}

}
