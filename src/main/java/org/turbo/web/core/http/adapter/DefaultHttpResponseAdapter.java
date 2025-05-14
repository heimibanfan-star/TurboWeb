package org.turbo.web.core.http.adapter;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.connect.ConnectSession;
import org.turbo.web.core.connect.InternalConnectSession;
import org.turbo.web.core.http.response.*;

/**
 * 默认的http响应适配器
 */
public class DefaultHttpResponseAdapter implements HttpResponseAdapter{
	private static final Logger log = LoggerFactory.getLogger(DefaultHttpResponseAdapter.class);

	@Override
	public ChannelFuture writeHttpResponse(HttpResponse response, ConnectSession session) {
		InternalConnectSession internalConnectSession = (InternalConnectSession) session;
		ChannelFuture channelFuture = internalConnectSession.getChannel().writeAndFlush(response);
		// 判断是否是文件下载响应
		if (response instanceof AbstractFileResponse abstractFileResponse) {
			ChannelFuture future = handleFileResponse(abstractFileResponse, internalConnectSession);
			if (future != null) {
				return future;
			}
		} else if (response instanceof SseResponse sseResponse) {
			handleSse(sseResponse, internalConnectSession);
		}
		return channelFuture;
	}

	/**
	 * 处理sse响应
	 *
	 * @param sseResponse sse的结果
	 * @param session 连接会话
	 */
	private void handleSse(SseResponse sseResponse, InternalConnectSession session) {
		sseResponse.startSse();
	}

	/**
	 * 处理文件下载响应
	 *
	 * @param response 文件下载响应
	 * @param session 连接会话
	 */
	private ChannelFuture handleFileResponse(AbstractFileResponse response, InternalConnectSession session) {
		ChannelFuture channelFuture = null;
		if (response instanceof FileStreamResponse fileStreamResponse) {
			// 处理分块文件传输的情况
			FileStream chunkedFile = fileStreamResponse.getChunkedFile();
			channelFuture = chunkedFile.readFileWithChunk((buf, e) -> {
				if (e == null) {
					return session.getChannel().writeAndFlush(new DefaultHttpContent(buf));
				} else {
					log.error("文件读取失败", e);
					session.getChannel().close();
					return null;
				}
			});
		}
		return channelFuture;
	}
}
