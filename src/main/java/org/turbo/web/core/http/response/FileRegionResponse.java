package org.turbo.web.core.http.response;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import org.turbo.web.exception.TurboFileException;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 实现文件零拷贝的响应对象
 */
public class FileRegionResponse extends AbstractFileResponse {

	private final FileRegion fileRegion;

	public FileRegionResponse(File file) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8);
	}

	public FileRegionResponse(File file, Charset filenameCharset) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, filenameCharset);
	}

	public FileRegionResponse(HttpVersion version, HttpResponseStatus status, File file, Charset filenameCharset) {
		super(version, status, file, filenameCharset);
		this.fileRegion = new DefaultFileRegion(file, 0, file.length());
		this.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
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
