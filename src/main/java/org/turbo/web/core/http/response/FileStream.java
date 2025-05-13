package org.turbo.web.core.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;

import java.util.function.BiFunction;

/**
 * 文件流接口
 */
public interface FileStream {


	/**
	 * 读取文件分块
	 *
	 * @param function 处理分块的回调
	 * @return 最后一个分块完成之后的回调
	 */
	ChannelFuture readFileWithChunk(BiFunction<ByteBuf, Exception, ChannelFuture> function);
}
