package top.turboweb.core.channel;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.SocketUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TurboWebNioServerSocketChannel extends NioServerSocketChannel {

    private final ExecutorService zeroCopyPool;

    public TurboWebNioServerSocketChannel(ExecutorService zeroCopyPool) {
        this.zeroCopyPool = zeroCopyPool;
    }

    private static final Logger log = LoggerFactory.getLogger(TurboWebNioServerSocketChannel.class);

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SocketChannel ch = SocketUtils.accept(javaChannel());
        try {
            if (ch != null) {
                buf.add(new TurboWebNioSocketChannel(this, ch, zeroCopyPool));
                return 1;
            }
        } catch (Throwable t) {
            log.warn("Failed to create a new channel from an accepted socket.", t);

            try {
                ch.close();
            } catch (Throwable t2) {
                log.warn("Failed to close a socket.", t2);
            }
        }
        return 0;
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        zeroCopyPool.shutdown();
    }
}
