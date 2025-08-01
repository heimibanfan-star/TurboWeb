package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboFileException;

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

	public DefaultFileStream(File file) throws IOException {
		this(file, 8192);
	}

	public DefaultFileStream(File file, int chunkSize) throws IOException {
		this.fileChannel = FileChannel.open(file.toPath());
		this.fileSize = fileChannel.size();
		this.chunkSize = chunkSize;
	}

	@Override
	public ChannelFuture readFileWithChunk(BiFunction<ByteBuf, Exception, ChannelFuture> function, Runnable success) {
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
					channelFuture = function.apply(buf, null);
				} else {
					function.apply(null, exception);
					return channelFuture;
				}
			}
		} finally {
			try {
				closeFileChannel(fileChannel);
				success.run();
			} catch (Exception e) {
				log.error("成功回调调用失败", e);
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
