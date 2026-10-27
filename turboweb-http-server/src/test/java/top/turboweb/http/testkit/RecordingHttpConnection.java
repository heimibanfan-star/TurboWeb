package top.turboweb.http.testkit;

import top.turboweb.http.connection.HttpConnection;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SSE 用例用的记录型 {@link HttpConnection}：把 {@code write} 的数据按 UTF-8 解码后按序记下，
 * 记录 {@code close} 次数，并允许手动触发已注册的 closeListener。
 *
 * <p>这是纯内存桩，不持有通道/句柄；厂商在真实连接上的写出路径由 L3 覆盖。
 */
public class RecordingHttpConnection implements HttpConnection {

    private final List<String> sent = new ArrayList<>();
    private final List<Runnable> closeListeners = new ArrayList<>();
    private int closeCount;

    @Override
    public CompletableFuture<Void> write(byte[] bytes) {
        sent.add(new String(bytes, StandardCharsets.UTF_8));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void closeListener(Runnable runnable) {
        closeListeners.add(runnable);
    }

    @Override
    public CompletableFuture<Void> close() {
        closeCount++;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    public SocketAddress localAddress() {
        return null;
    }

    /** 已写出的消息（按调用顺序，UTF-8 解码）。 */
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
