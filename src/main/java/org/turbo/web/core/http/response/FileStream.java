package org.turbo.web.core.http.response;

import io.netty.buffer.ByteBuf;

import java.util.function.BiConsumer;

/**
 * 文件流接口
 */
public interface FileStream {


	/**
	 * 读取文件分块
	 * @param consumer 处理分块的回调
	 */
	void readFileWithChunk(BiConsumer<ByteBuf, Exception> consumer);
}
