package org.turboweb.core.piplines;

import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.dispatch.HttpProtocolDispatcher;
import org.turboweb.gateway.Gateway;
import org.turboweb.http.scheduler.HttpScheduler;
import org.turboweb.websocket.dispatch.WebSocketDispatcherHandler;

/**
 * http协议分发器
 */
public class TurboChannelHandler extends ChannelInitializer<NioSocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(TurboChannelHandler.class);
    private final int maxContentLength;
    private final HttpProtocolDispatcher httpProtocolDispatcher;
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
        this.httpProtocolDispatcher = new HttpProtocolDispatcher(httpScheduler, webSocketDispatcherHandler, websocketPath, gateway);
        this.gateway = gateway;
    }

    @Override
    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
        ChannelPipeline pipeline = nioSocketChannel.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(maxContentLength));
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(httpProtocolDispatcher);
    }
}
