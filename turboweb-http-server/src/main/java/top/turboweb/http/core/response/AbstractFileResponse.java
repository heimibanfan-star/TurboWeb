package top.turboweb.http.core.response;

import io.netty.handler.codec.http.*;
import top.turboweb.http.exception.ResponseHandleException;
import top.turboweb.http.core.ContentType;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * 抽象的文件响应对象，用于将文件以 HTTP 响应形式返回客户端。
 * <p>
 * 提供文件校验、响应头初始化和文件名编码等基础功能。
 * 支持通过文件名或 {@link File} 对象构造响应。
 * </p>
 * <p>
 * 构造时可指定文件名编码字符集立即写出 Content-Disposition；
 * 不指定（字符集为 null）时文件名原文暂存，Content-Disposition 留待结果转化环节
 * 以装配期判定的缺省字符集经 {@link #finalizeFilenameEncoding(Charset)} 补写；
 * 显式指定与留待缺省补写互斥，已写出该头的响应不会被补写覆盖。
 * </p>
 */
public abstract class AbstractFileResponse extends DefaultHttpResponse {

	/** 文件名原文，供留待补写 Content-Disposition 时使用 */
	private final String rawFilename;

	/**
	 * 使用 {@link File} 构造文件响应
	 *
	 * @param status          HTTP 响应状态
	 * @param file            文件对象
	 * @param filenameCharset 文件名编码字符集；为 null 表示不立即编码，
	 *                        Content-Disposition 留待 {@link #finalizeFilenameEncoding(Charset)} 补写
	 */
	protected AbstractFileResponse(HttpResponseStatus status, File file, Charset filenameCharset) {
		super(HttpVersion.HTTP_1_1, status);
		fileVerify(file);
		this.rawFilename = file.getName();
		initHeaders(filenameCharset);
	}

	/**
	 * 使用文件名构造文件响应
	 *
	 * @param status          HTTP 响应状态
	 * @param filename        文件名
	 * @param filenameCharset 文件名编码字符集；为 null 表示不立即编码，
	 *                        Content-Disposition 留待 {@link #finalizeFilenameEncoding(Charset)} 补写
	 */
	protected AbstractFileResponse(HttpResponseStatus status, String filename, Charset filenameCharset) {
		super(HttpVersion.HTTP_1_1, status);
		this.rawFilename = Objects.requireNonNull(filename, "filename can not be null");
		initHeaders(filenameCharset);
	}

	/**
	 * 校验文件有效性
	 *
	 * @param file 文件对象
	 * @throws ResponseHandleException 如果文件不存在、为文件夹或不可读
	 */
	private void fileVerify(File file) {
		if (!file.exists()) {
			throw new ResponseHandleException("File does not exist: " + file.getAbsolutePath());
		}
		if (file.isDirectory()) {
			throw new ResponseHandleException("File is a directory: " + file.getAbsolutePath());
		}
		if (!file.canRead()) {
			throw new ResponseHandleException("File is not readable: " + file.getAbsolutePath());
		}
	}

	/**
	 * 初始化文件响应的 HTTP 头：Content-Type 与 Connection 恒写，
	 * Content-Disposition 仅在给定字符集时立即以该字符集编码文件名写出
	 *
	 * @param charset 文件名编码字符集，可为 null（留待补写）
	 */
	private void initHeaders(Charset charset) {
		this.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
		if (charset != null) {
			this.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeFileName(rawFilename, charset) + "\"");
		}
		this.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
	}

	/**
	 * 以给定字符集补写文件名的 Content-Disposition 头。
	 * <p>构造期已显式编码写出该头的响应不改动；由结果转化环节在写出前以缺省字符集调用一次。</p>
	 *
	 * @param charset 文件名编码字符集，不能为null
	 * @throws NullPointerException charset 为 null
	 */
	public void finalizeFilenameEncoding(Charset charset) {
		Objects.requireNonNull(charset, "charset can not be null");
		if (!this.headers().contains(HttpHeaderNames.CONTENT_DISPOSITION)) {
			this.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + encodeFileName(rawFilename, charset) + "\"");
		}
	}

	/**
	 * 对文件名进行 URL 编码，确保中文和特殊字符正确显示
	 *
	 * @param fileName        文件名
	 * @param filenameCharset 编码字符集
	 * @return 编码后的文件名
	 */
	private String encodeFileName(String fileName, Charset filenameCharset) {
		// URL 编码，并把表单口径的空格加号还原为 %20
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
