package org.turbo.web.core.http.response;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.*;
import org.turbo.web.exception.TurboFileException;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 实现文件零拷贝的响应对象
 */
public class FileRegionResponse extends DefaultHttpResponse {

	private final FileRegion fileRegion;
	private final Charset filenameCharset;

	public FileRegionResponse(File file) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8);
	}

	public FileRegionResponse(File file, Charset filenameCharset) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, filenameCharset);
	}

	public FileRegionResponse(HttpVersion version, HttpResponseStatus status, File file, Charset filenameCharset) {
		super(version, status);
		this.filenameCharset = filenameCharset;
		this.init(file);
		this.fileRegion = new DefaultFileRegion(file, 0, file.length());
	}

	/**
	 * 初始化
	 *
	 * @param file 文件
	 */
	protected void init(File file) {
		this.fileVerify(file);
		this.initHeaders(file);
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
	private void initHeaders(File file) {
		this.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
		this.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeFileName(file.getName()) + "\"");
		this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
	}

	private String encodeFileName(String fileName) {
		// 使用 UTF-8 编码，并对中文进行转码处理
		return URLEncoder.encode(fileName, filenameCharset).replace("+", "%20");
	}

	/**
	 * 获取文件零拷贝对象
	 *
	 * @return 文件零拷贝对象
	 */
	public FileRegion getFileRegion() {
		return fileRegion;
	}
}
