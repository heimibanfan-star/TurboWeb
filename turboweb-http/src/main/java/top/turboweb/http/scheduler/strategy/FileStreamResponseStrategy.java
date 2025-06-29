package top.turboweb.http.scheduler.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.FileStream;
import top.turboweb.http.response.FileStreamResponse;


/**
 * 处理文件流响应的策略
 */
public class FileStreamResponseStrategy extends ResponseStrategy {
    private static final Logger log = LoggerFactory.getLogger(FileStreamResponseStrategy.class);

    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        if (response instanceof FileStreamResponse fileStreamResponse) {
            // 写入响应头
            session.getChannel().writeAndFlush(fileStreamResponse);
            return handleFileStream(fileStreamResponse, session);
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }

    /**
     * 进行文件流的下载
     *
     * @param fileStreamResponse 文件下载的响应读写
     * @param session 连接的 session
     * @return 异步监听对象
     */
    private ChannelFuture handleFileStream(FileStreamResponse fileStreamResponse, InternalConnectSession session) {
        DefaultChannelPromise future = new DefaultChannelPromise(session.getChannel());
        DiskOpeThreadUtils.execute(() -> {
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
                        future.setFailure(new TurboFileException("file download fail"));
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
        return future;
    }
}
