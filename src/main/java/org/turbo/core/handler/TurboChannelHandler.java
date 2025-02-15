package org.turbo.core.handler;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.core.handler.piplines.HttpWorkerDispatcherHandler;
import org.turbo.core.http.execetor.HttpExecuteAdaptor;

/**
 * 通道处理器
 */
public class TurboChannelHandler extends ChannelInitializer<NioSocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(TurboChannelHandler.class);
    private final int maxContentLength;
    private final HttpExecuteAdaptor httpExecuteAdaptor;

    public TurboChannelHandler(HttpExecuteAdaptor httpExecuteAdaptor, int maxContentLength) {
        super();
        this.maxContentLength = maxContentLength;
        this.httpExecuteAdaptor = httpExecuteAdaptor;
    }

    @Override
    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
        ChannelPipeline pipeline = nioSocketChannel.pipeline();
        pipeline.addLast(new LoggingHandler());
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(maxContentLength));
        pipeline.addLast(new HttpWorkerDispatcherHandler(httpExecuteAdaptor));
    }
}
