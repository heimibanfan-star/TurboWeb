package org.turboweb.core.http.response;

import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.*;
import org.turboweb.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 流式文件传输的响应结果
 */
public class FileStreamResponse extends AbstractFileResponse{

	private final FileStream chunkedFile;
	private ChannelProgressiveFutureListener listener;

	public FileStreamResponse(File file, boolean backPress) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, 8192, backPress);
	}

	public FileStreamResponse(File file) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, 8192, true);
	}

	public FileStreamResponse(File file, Charset filenameCharset, boolean backPress) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, filenameCharset, 8192, backPress);
	}

	public FileStreamResponse(File file, int chunkSize, boolean backPress) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, chunkSize, backPress);
	}

	public FileStreamResponse(HttpVersion version, HttpResponseStatus status, File file, Charset filenameCharset, int chunkSize, boolean backPress) {
		super(version, status, file, filenameCharset);
		try {
			chunkedFile = new DefaultFileStream(file, chunkSize, backPress);
		} catch (IOException e) {
			throw new TurboFileException(e);
		}
		this.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
	}

	public FileStream getChunkedFile() {
		return chunkedFile;
	}

	public ChannelProgressiveFutureListener getListener() {
		return listener;
	}
}
