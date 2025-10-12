package top.turboweb.gateway.filter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * 默认的响应处理器
 */
public class DefaultResponseHelper implements ResponseHelper{

    private final ChannelHandlerContext ctx;

    public DefaultResponseHelper(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isResponse() {
        return false;
    }
}
