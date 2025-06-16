package top.turboweb.http.response;

import com.sun.management.OperatingSystemMXBean;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 流式文件传输的响应结果
 */
public class FileStreamResponse extends AbstractFileResponse{

	private static final Logger log = LoggerFactory.getLogger(FileStreamResponse.class);
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
			boolean openProtectMemory = openProtectMemory(file.length());
			if (!backPress && openProtectMemory) {
				log.warn("file {} size {} out of limit, use BackPressFileStream", file.getName(), file.length());
			}
			if (backPress || openProtectMemory) {
				chunkedFile = new BackPressFileStream(file, chunkSize);
			} else {
				chunkedFile = new DefaultFileStream(file, chunkSize);
			}
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
}
