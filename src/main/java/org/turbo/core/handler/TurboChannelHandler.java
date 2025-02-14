package org.turbo.core.handler;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import org.turbo.core.handler.piplines.HttpWorkerDispatcherHandler;

/**
 * 通道处理器
 */
public class TurboChannelHandler extends ChannelInitializer<NioSocketChannel> {

    private final int maxContentLength;

    public TurboChannelHandler(int maxContentLength) {
        super();
        this.maxContentLength = maxContentLength;
    }

    @Override
    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
        ChannelPipeline pipeline = nioSocketChannel.pipeline();
        pipeline.addLast(new LoggingHandler());
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(maxContentLength));
        pipeline.addLast(new HttpWorkerDispatcherHandler());
    }
}
