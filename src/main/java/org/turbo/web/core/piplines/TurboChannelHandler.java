package org.turbo.web.core.piplines;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.gateway.Gateway;
import org.turbo.web.core.http.scheduler.HttpScheduler;

/**
 * 通道处理器
 */
public class TurboChannelHandler extends ChannelInitializer<NioSocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(TurboChannelHandler.class);
    private final int maxContentLength;
    private final HttpWorkerDispatcherHandler httpWorkerDispatcherHandler;
    private final Gateway gateway;

    public TurboChannelHandler(
        HttpScheduler httpScheduler,
        int maxContentLength,
        WebSocketDispatcherHandler webSocketDispatcherHandler,
        String websocketPath,
        Gateway gateway
    ) {
        super();
        this.maxContentLength = maxContentLength;
        this.httpWorkerDispatcherHandler = new HttpWorkerDispatcherHandler(httpScheduler, webSocketDispatcherHandler, websocketPath, gateway);
        this.gateway = gateway;
    }

    @Override
    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
        ChannelPipeline pipeline = nioSocketChannel.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(maxContentLength));
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(httpWorkerDispatcherHandler);
    }
}
