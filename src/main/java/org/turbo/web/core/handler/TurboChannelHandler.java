package org.turbo.web.core.handler;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.handler.piplines.HttpWorkerDispatcherHandler;
import org.turbo.web.core.http.execetor.HttpScheduler;

/**
 * 通道处理器
 */
public class TurboChannelHandler extends ChannelInitializer<NioSocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(TurboChannelHandler.class);
    private final int maxContentLength;
    private final HttpWorkerDispatcherHandler httpWorkerDispatcherHandler;

    public TurboChannelHandler(HttpScheduler httpScheduler, int maxContentLength) {
        super();
        this.maxContentLength = maxContentLength;
        this.httpWorkerDispatcherHandler = new HttpWorkerDispatcherHandler(httpScheduler);
    }

    @Override
    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
        ChannelPipeline pipeline = nioSocketChannel.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(maxContentLength));
        pipeline.addLast(httpWorkerDispatcherHandler);
    }
}
