package top.turboweb.core.channel;

import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TurboWebNioServerSocketChannel extends NioServerSocketChannel {

    private final ExecutorService zeroCopyPool;

    public TurboWebNioServerSocketChannel(ExecutorService zeroCopyPool) {
        this.zeroCopyPool = zeroCopyPool;
    }

    private static final InternalLogger log = InternalLoggerFactory.getInstance(TurboWebNioServerSocketChannel.class);

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
