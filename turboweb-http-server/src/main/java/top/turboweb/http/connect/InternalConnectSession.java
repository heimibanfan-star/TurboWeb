package top.turboweb.http.connect;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 服务器内部使用的连接会话
 */
public class InternalConnectSession extends ConnectSession{

    /**
     * 每个channel拥有自己的一把锁，保护对该channel的写入原子性
     */
    public final ReentrantLock channelLock = new ReentrantLock();

    public InternalConnectSession(Channel channel) {
        super(channel);
    }

    public Channel getChannel() {
        return channel;
    }

    public EventLoop getExecutor() {
        return channel.eventLoop();
    }
}
