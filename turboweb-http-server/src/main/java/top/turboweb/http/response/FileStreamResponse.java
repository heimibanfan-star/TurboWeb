package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * 基于文件流的 HTTP 响应结果，用于大文件的分块传输。
 * <p>
 * 该类继承 {@link AbstractFileResponse}，支持按指定块大小读取文件并流式发送给客户端，
 * 可处理部分文件范围传输（offset + length）。可用于避免一次性将大文件加载到内存中。
 * </p>
 */
public class FileStreamResponse extends AbstractFileResponse implements InternalCallResponse {

	private static final Logger log = LoggerFactory.getLogger(FileStreamResponse.class);

	/** 文件通道，用于流式读取文件 */
	private final FileChannel fileChannel;
	/** 文件结束位置 = offset + length */
	private final long end;
	/** 分块读取大小 */
	private final long chunkSize;
	/** 文件名编码字符集 */
	private final Charset filenameCharset;
	/** 文件起始偏移 */
	private final long offset;
	/** 文件传输长度 */
	private final long length;

	/**
	 * 构造默认 FileStreamResponse
	 * @param file 文件
	 */
	public FileStreamResponse(File file) {
		this(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset(), 2097152, 0, file.length());
	}

	/**
	 * 构造 FileStreamResponse，支持自定义文件名编码
	 * @param file 文件
	 * @param filenameCharset 文件名编码字符集
	 */
	public FileStreamResponse(File file, Charset filenameCharset) {
		this(HttpResponseStatus.OK, file, filenameCharset, 2097152, 0, file.length());
	}

	/**
	 * 构造 FileStreamResponse，支持自定义分块大小
	 * @param file 文件
	 * @param chunkSize 分块大小
	 */
	public FileStreamResponse(File file, int chunkSize) {
		this(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset(), chunkSize, 0, file.length());
	}

	/**
	 * 构造 FileStreamResponse，支持指定偏移和长度
	 * @param file 文件
	 * @param offset 起始偏移
	 * @param length 读取长度
	 */
	public FileStreamResponse(File file, long offset, long length) {
		this(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset(), 2097152, offset, length);
	}

	/**
	 * 完整构造方法
	 * @param status 响应状态
	 * @param file 文件
	 * @param filenameCharset 文件名编码字符集
	 * @param chunkSize 分块大小
	 * @param offset 文件起始偏移
	 * @param length 文件读取长度
	 */
	public FileStreamResponse(HttpResponseStatus status, File file, Charset filenameCharset, int chunkSize, long offset, long length) {
		super(status, file, filenameCharset);
		this.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
        try {
            this.fileChannel = FileChannel.open(file.toPath());
        } catch (IOException e) {
            throw new TurboFileException(e);
        }
        this.chunkSize = chunkSize;
		this.filenameCharset = filenameCharset;
		this.offset = offset;
		this.length = length;
		this.end = offset + length;
	}


	@Override
	public InternalCallType getType() {
		return InternalCallType.FILE_STREAM;
	}

	public FileChannel getFileChannel() {
		return fileChannel;
	}

	public long getChunkSize() {
		return chunkSize;
	}

	public Charset getFilenameCharset() {
		return filenameCharset;
	}

	public long getOffset() {
		return offset;
	}

	public long getLength() {
		return length;
	}

	public long getEnd() {
		return end;
	}
}
