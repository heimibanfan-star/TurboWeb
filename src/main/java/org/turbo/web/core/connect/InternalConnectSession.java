package org.turbo.web.core.connect;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;

/**
 * 服务器内部使用的连接会话
 */
public class InternalConnectSession extends ConnectSession{

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
