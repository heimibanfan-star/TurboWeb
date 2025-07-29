package top.turboweb.core.handler;

import io.netty.channel.ChannelHandler;

/**
 * 用于生成 ChannelHandler
 */
public interface ChannelHandlerFactory {
    ChannelHandler create();
}
