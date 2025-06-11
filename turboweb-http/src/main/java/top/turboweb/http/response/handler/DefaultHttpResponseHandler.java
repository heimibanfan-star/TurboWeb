package top.turboweb.http.response.handler;

import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.utils.thread.BackupThreadUtils;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.*;

/**
 * 默认的http响应处理器
 */
public class DefaultHttpResponseHandler implements HttpResponseHandler {
	private static final Logger log = LoggerFactory.getLogger(DefaultHttpResponseHandler.class);

	@Override
	public ChannelFuture writeHttpResponse(HttpResponse response, ConnectSession session) {
		if (response instanceof IgnoredHttpResponse) {
			return null;
		}
		InternalConnectSession internalConnectSession = (InternalConnectSession) session;
		ChannelFuture channelFuture = internalConnectSession.getChannel().writeAndFlush(response);
		// 判断是否是文件下载响应
		if (response instanceof AbstractFileResponse abstractFileResponse) {
			return handleFileResponse(abstractFileResponse, internalConnectSession);
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
		DefaultChannelPromise future = new DefaultChannelPromise(session.getChannel());
		if (response instanceof FileStreamResponse fileStreamResponse) {
			BackupThreadUtils.execute(() -> {
				log.debug("File download is handed over to backup thread pool");
                try {
                    // 处理分块文件传输的情况
                    FileStream chunkedFile = fileStreamResponse.getChunkedFile();
                    ChannelFuture channelFuture = chunkedFile.readFileWithChunk((buf, e) -> {
                        if (e == null) {
                            return session.getChannel().writeAndFlush(new DefaultHttpContent(buf));
                        } else {
                            log.error("文件读取失败", e);
                            session.getChannel().close();
                            return null;
                        }
                    });
                    if (channelFuture == null) {
                        future.setFailure(new TurboFileException("file download fail"));
                    } else {
                        channelFuture.addListener(f -> {
                            if (f.isSuccess()) {
                                future.setSuccess();
                            } else {
                                future.setFailure(f.cause());
                            }
                        });
                    }
                } catch (Exception e) {
                    future.setFailure(e);
                }
            });
		} else if (response instanceof FileRegionResponse fileRegionResponse) {
			session.getChannel().writeAndFlush(fileRegionResponse.getFileRegion())
					.addListener(f -> {
						if (f.isSuccess()) {
							future.setSuccess();
						} else {
							future.setFailure(f.cause());
						}
					});
		} else {
			future.setFailure(new TurboFileException("file download fail:not support response type"));
		}
		return future;
	}
}
