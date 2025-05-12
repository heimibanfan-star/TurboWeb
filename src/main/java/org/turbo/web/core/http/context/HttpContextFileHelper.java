package org.turbo.web.core.http.context;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;
import org.turbo.web.anno.SyncOnce;
import org.turbo.web.exception.TurboFileException;
import org.turbo.web.exception.TurboResponseRepeatWriteException;
import reactor.util.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * httpContext的文件扩展功能
 */
public class HttpContextFileHelper {

	private final HttpContext ctx;

	public HttpContextFileHelper(HttpContext ctx) {
		this.ctx = ctx;
	}

	/**
	 * 响应文件
	 *
	 * @param status      响应状态
	 * @param bytes         文件内容
	 * @param contentType 文件类型
	 * @param fileName    文件名
	 * @param isInline    是否内联
	 * @return null
	 */
	@SyncOnce
	public Object file(HttpResponseStatus status, byte[] bytes, ContentType contentType, @Nullable String fileName, boolean isInline) {
		if (ctx.isWrite()) {
			throw new TurboResponseRepeatWriteException("response repeat write");
		}
		if (!isInline) {
			return ctx.download(status, bytes, fileName);
		} else {
			ctx.getResponse().setContent(bytes);
			ctx.getResponse().setContentType(contentType.getMimeType());
			String filenameHeader = "";
			if (fileName != null && !fileName.isEmpty()) {
				filenameHeader = "filename=\"" + fileName + "\"";
			}
			ctx.getResponse().headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline; " + filenameHeader);
			ctx.getResponse().setStatus(status);
			ctx.setWrite();
			return ctx.end();
		}
	}

	/**
	 * 响应文件
	 *
	 * @param bytes         文件内容
	 * @param contentType 文件类型
	 * @param fileName    文件名
	 * @param isInline    是否内联
	 * @return null
	 */
	@SyncOnce
	public Object file(byte[] bytes, ContentType contentType, @Nullable String fileName, boolean isInline) {
		return file(HttpResponseStatus.OK, bytes, contentType, fileName, isInline);
	}

	@SyncOnce
	public Object file(byte[] bytes, ContentType contentType, @Nullable String fileName) {
		return file(bytes, contentType, fileName, true);
	}

	@SyncOnce
	public Object file(byte[] bytes, ContentType contentType) {
		return file(bytes, contentType, null);
	}


	@SyncOnce
	public Object file(HttpResponseStatus status, InputStream inputStream, ContentType contentType, @Nullable String fileName, boolean isInline) {
		try {
			return file(status, inputStream.readAllBytes(), contentType, fileName, isInline);
		} catch (IOException e) {
			throw new TurboFileException(e);
		}
	}

	@SyncOnce
	public Object file(InputStream inputStream, ContentType contentType, @Nullable String fileName, boolean isInline) {
		return file(HttpResponseStatus.OK, inputStream, contentType, fileName, isInline);
	}

	@SyncOnce
	public Object file(InputStream inputStream, ContentType contentType, @Nullable String fileName) {
		return file(inputStream, contentType, fileName, true);
	}

	@SyncOnce
	public Object file(InputStream inputStream, ContentType contentType) {
		return file(inputStream, contentType, null);
	}

	@SyncOnce
	public Object png(HttpResponseStatus status, byte[] bytes) {
		return file(status, bytes, ContentType.IMAGE_PNG, null, true);
	}

	@SyncOnce
	public Object png(byte[] bytes) {
		return png(HttpResponseStatus.OK, bytes);
	}

	@SyncOnce
	public Object png(HttpResponseStatus status, InputStream inputStream) {
		return file(status, inputStream, ContentType.IMAGE_PNG, null, true);
	}

	@SyncOnce
	public Object png(InputStream inputStream) {
		return png(HttpResponseStatus.OK, inputStream);
	}

	@SyncOnce
	public Object jpeg(HttpResponseStatus status, byte[] bytes) {
		return file(status, bytes, ContentType.IMAGE_JPEG, null, true);
	}

	@SyncOnce
	public Object jpeg(byte[] bytes) {
		return jpeg(HttpResponseStatus.OK, bytes);
	}

	@SyncOnce
	public Object jpeg(HttpResponseStatus status, InputStream inputStream) {
		return file(status, inputStream, ContentType.IMAGE_JPEG, null, true);
	}

	@SyncOnce
	public Object jpeg(InputStream inputStream) {
		return jpeg(HttpResponseStatus.OK, inputStream);
	}

	@SyncOnce
	public Object gif(HttpResponseStatus status, byte[] bytes) {
		return file(status, bytes, ContentType.IMAGE_GIF, null, true);
	}

	@SyncOnce
	public Object gif(byte[] bytes) {
		return gif(HttpResponseStatus.OK, bytes);
	}

	@SyncOnce
	public Object gif(HttpResponseStatus status, InputStream inputStream) {
		return file(status, inputStream, ContentType.IMAGE_GIF, null, true);
	}

	@SyncOnce
	public Object gif(InputStream inputStream) {
		return gif(HttpResponseStatus.OK, inputStream);
	}

	@SyncOnce
	public Object pdf(HttpResponseStatus status, byte[] bytes) {
		return file(status, bytes, ContentType.APPLICATION_PDF, null, true);
	}

	@SyncOnce
	public Object pdf(byte[] bytes) {
		return pdf(HttpResponseStatus.OK, bytes);
	}

	@SyncOnce
	public Object pdf(HttpResponseStatus status, InputStream inputStream) {
		return file(status, inputStream, ContentType.APPLICATION_PDF, null, true);
	}

	@SyncOnce
	public Object pdf(InputStream inputStream) {
		return pdf(HttpResponseStatus.OK, inputStream);
	}
}
