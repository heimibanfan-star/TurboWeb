package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.function.BiFunction;

/**
 * 文件流接口
 */
public interface FileStream {


	Logger log = LoggerFactory.getLogger(FileStream.class);

	/**
	 * 读取文件分块
	 *
	 * @param function 处理分块的回调
	 * @param success 成功之后的回调
	 * @return 最后一个分块完成之后的回调
	 */
	ChannelFuture readFileWithChunk(BiFunction<ByteBuf, Exception, ChannelFuture> function, Runnable success);

	/**
	 * 关闭文件通道
	 */
	default void closeFileChannel(FileChannel fileChannel) {
		try {
			fileChannel.close();
		} catch (IOException e) {
			log.error("文件关闭失败", e);
		}
	}

}
