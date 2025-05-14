package org.turbo.web.core.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.function.BiFunction;

/**
 * 默认的文件流
 */
public class DefaultFileStream implements FileStream {

	private static final Logger log = LoggerFactory.getLogger(DefaultFileStream.class);
	private final FileChannel fileChannel;
	private final long fileSize;
	private final int chunkSize;
	private long offset;
	private final Object lock = new Object();
	private final boolean backPress;

	public DefaultFileStream(File file) throws IOException {
		this(file, 8192, true);
	}

	public DefaultFileStream(File file, int chunkSize) throws IOException {
		this(file, chunkSize, true);
	}

	public DefaultFileStream(File file, boolean backPress) throws IOException {
		this(file, 8192, backPress);
	}

	public DefaultFileStream(File file, int chunkSize, boolean backPress) throws IOException {
		this.fileChannel = FileChannel.open(file.toPath());
		this.fileSize = fileChannel.size();
		this.chunkSize = chunkSize;
		this.backPress = backPress;
	}

	@Override
	public ChannelFuture readFileWithChunk(BiFunction<ByteBuf, Exception, ChannelFuture> function) {
		Exception exception = null;
		ChannelFuture channelFuture = null;
		int bufSize = chunkSize > fileSize ? (int) fileSize : chunkSize;
		try {
			while (hasNextChunk()) {
				ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer(bufSize);
				// 尝试读取文件
				try {
					readChunk(buf);
				} catch (IOException e) {
					exception = e;
				}
				// 判断是否有异常产生
				if (exception == null) {
					try {
						channelFuture = function.apply(buf, null);
						if (backPress) {
							channelFuture.addListener(future -> {
								synchronized (lock) {
									lock.notify();
								}
							});
							synchronized (lock) {
								lock.wait(20);
							}
						}
					} catch (Exception ignored) {
					}
				} else {
					function.apply(null, exception);
					return channelFuture;
				}
			}
		} finally {
			try {
				fileChannel.close();
			} catch (IOException e) {
				log.error("文件关闭失败", e);
			}
		}
		return channelFuture;
	}

	/**
	 * 是否存在未读取的分块
	 *
	 *
	 * @return boolean
	 */
	private boolean hasNextChunk() {
		return offset < fileSize;
	}

	/**
	 * 读取文件分块
	 *
	 *
	 * @param buf ByteBuf
	 * @throws IOException IOException
	 */
	private void readChunk(ByteBuf buf) throws IOException {
		int writerIndex = buf.writerIndex();
		int read = fileChannel.read(buf.nioBuffer(writerIndex, buf.writableBytes()));
		if (read <= 0) {
			throw new TurboFileException("文件读取失败");
		}
		buf.writerIndex(writerIndex + read);
		offset += read;
	}
}
