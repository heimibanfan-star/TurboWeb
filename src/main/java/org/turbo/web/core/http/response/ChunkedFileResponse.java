package org.turbo.web.core.http.response;

import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.stream.ChunkedFile;
import org.apache.commons.lang3.function.TriConsumer;
import org.turbo.web.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 分块文件传输的响应结果
 */
public class ChunkedFileResponse extends AbstractFileResponse{

	private final ChunkedFile chunkedFile;
	private ChannelProgressiveFutureListener listener;

	public ChunkedFileResponse(File file) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, 8192);
	}

	public ChunkedFileResponse(File file, Charset filenameCharset) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, filenameCharset, 8192);
	}

	public ChunkedFileResponse(File file, int chunkSize) {
		this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, chunkSize);
	}

	protected ChunkedFileResponse(HttpVersion version, HttpResponseStatus status, File file, Charset filenameCharset, int chunkSize) {
		super(version, status, file, filenameCharset);
		try {
			chunkedFile = new ChunkedFile(file, chunkSize);
		} catch (IOException e) {
			throw new TurboFileException(e);
		}
	}

	public ChunkedFile getChunkedFile() {
		return chunkedFile;
	}

	public ChannelProgressiveFutureListener getListener() {
		return listener;
	}

	/**
	 * 添加进度监听器
	 *
	 * @param progressed 进度回调 (结果，当前已传输的字节数， 总共需要传输的字节数)
	 * @param completed  完成回调
	 */
	public void listener(TriConsumer<ChannelProgressiveFuture, Long, Long> progressed, Consumer<ChannelProgressiveFuture> completed) {
		listener = new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture channelProgressiveFuture, long l, long l1) throws Exception {
				progressed.accept(channelProgressiveFuture, l, l1);
			}

			@Override
			public void operationComplete(ChannelProgressiveFuture channelProgressiveFuture) throws Exception {
				completed.accept(channelProgressiveFuture);
			}
		};
	}
}
