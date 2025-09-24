package top.turboweb.http.response;

import com.sun.management.OperatingSystemMXBean;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * 流式文件传输的响应结果
 */
public class FileStreamResponse extends AbstractFileResponse implements InternalCallResponse {

	private static final Logger log = LoggerFactory.getLogger(FileStreamResponse.class);
	private final FileChannel fileChannel;
	private final long end;
	private final long chunkSize;
	private final Charset filenameCharset;
	private final long offset;
	private final long length;

	public FileStreamResponse(File file) {
		this(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset(), 2097152, 0, file.length());
	}

	public FileStreamResponse(File file, Charset filenameCharset) {
		this(HttpResponseStatus.OK, file, filenameCharset, 2097152, 0, file.length());
	}

	public FileStreamResponse(File file, int chunkSize) {
		this(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset(), chunkSize, 0, file.length());
	}

	public FileStreamResponse(File file, long offset, long length) {
		this(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset(), 2097152, offset, length);
	}

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

	/**
	 * 检测是否需要开启内存保护
	 * @param fileSize 文件大小
	 * @return 是否需要开启内存保护
	 */
	private boolean openProtectMemory(long fileSize) {
		if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean operatingSystemMXBean) {
			long freeMemorySize = operatingSystemMXBean.getFreeMemorySize();
			if (fileSize <= 0) {
				return false;
			}
			return freeMemorySize / fileSize < 2;
		}
		return true;
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
