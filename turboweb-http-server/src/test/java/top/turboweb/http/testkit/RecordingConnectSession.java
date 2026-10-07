package top.turboweb.http.testkit;

import io.netty.channel.ChannelFuture;
import top.turboweb.http.connect.ConnectSession;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * SSE 用例用的记录型 {@link ConnectSession}：把 {@code send} 的消息按序记下，
 * 记录 {@code close} 次数，并允许手动触发已注册的 closeListener。
 *
 * <p>这是纯内存桩，不持有通道/句柄；厂商在真实连接上的写出路径由 L3 覆盖。
 */
public class RecordingConnectSession implements ConnectSession {

    private final List<String> sent = new ArrayList<>();
    private final List<Runnable> closeListeners = new ArrayList<>();
    private int closeCount;

    @Override
    public ChannelFuture send(String message) {
        sent.add(message);
        return null;
    }

    @Override
    public void closeListener(Runnable runnable) {
        closeListeners.add(runnable);
    }

    @Override
    public void close() {
        closeCount++;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return null;
    }

    /** 已写出的消息（按调用顺序）。 */
    public List<String> sentMessages() {
        return sent;
    }

    /** close() 被调用的次数。 */
    public int closeCount() {
        return closeCount;
    }

    /** 模拟连接关闭：触发所有已注册的 closeListener。 */
    public void fireCloseListeners() {
        closeListeners.forEach(Runnable::run);
    }
}
