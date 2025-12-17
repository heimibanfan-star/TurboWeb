package top.turboweb.http.connect;

import io.netty.channel.ChannelFuture;

import java.net.SocketAddress;

/**
 * sse的回话对象
 */
public interface ConnectSession {

    /**
     * 向浏览器写入内容
     *
     * @param message 消息
     */
    ChannelFuture send(String message);

    /**
     * 当连接关闭时触发的回调
     *
     * @param runnable 回调
     */
    void closeListener(Runnable runnable);

    /**
     * 关闭连接
     */
    void close();

    /**
     * 获取远程地址
     *
     * @return 远程地址
     */
    SocketAddress getRemoteAddress();

    /**
     * 获取本地地址
     *
     * @return 本地地址
     */
    SocketAddress getLocalAddress();

}
