package org.turbo.web.core.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.exception.TurboFileException;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.function.BiConsumer;

/**
 * 默认的文件流
 */
public class DefaultFileStream implements FileStream {

	private static final Logger log = LoggerFactory.getLogger(DefaultFileStream.class);
	private final FileChannel fileChannel;
	private final long fileSize;
	private final int chunkSize;
	private long offset;

	public DefaultFileStream(File file) throws IOException {
		this(file, 8192);
	}

	public DefaultFileStream(File file, int chunkSize) throws IOException {
		this.fileChannel = FileChannel.open(file.toPath());
		this.fileSize = fileChannel.size();
		this.chunkSize = chunkSize;
	}

	@Override
	public void readFileWithChunk(BiConsumer<ByteBuf, Exception> consumer) {
		Exception exception = null;
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
					consumer.accept(buf, null);
				} else {
					consumer.accept(null, exception);
					return;
				}
			}
		} finally {
			try {
				fileChannel.close();
			} catch (IOException e) {
				log.error("文件关闭失败", e);
			}
		}
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
