package top.turboweb.http.testkit;

import io.netty.channel.ChannelFuture;
import top.turboweb.http.server.BootStrapWebServer;
import top.turboweb.http.server.application.Application;
import top.turboweb.http.server.config.ServerConfig;

import java.net.InetSocketAddress;

/**
 * 端到端测试的服务器夹具：在临时端口上起一台真实服务器，提供 baseUrl 与 close。
 * <p>
 * 端口用 {@code start(0)} 让系统分配，绑定完成后从 channel 的 localAddress 读回，
 * 因此返回的实例一定是"已可接受连接"的状态。关闭只负责发起，不等待 Netty 的静默期，
 * 也不断言线程终止（spec §7.3-7.4）。
 */
public final class Servers implements AutoCloseable {

    private final BootStrapWebServer server;
    private final int port;

    private Servers(BootStrapWebServer server, int port) {
        this.server = server;
        this.port = port;
    }

    /**
     * 以默认服务器配置装配给定应用并启动，返回已就绪的夹具实例。
     * 绑定失败会关掉服务器再抛出，避免线程泄漏。
     */
    public static Servers start(Application... applications) {
        return start(ServerConfig.builder().build(), applications);
    }

    public static Servers start(ServerConfig serverConfig, Application... applications) {
        BootStrapWebServer server = new BootStrapWebServer(serverConfig);
        for (Application application : applications) {
            server.registerApplicationLast(application);
        }
        ChannelFuture bindFuture;
        try {
            bindFuture = server.start(0).sync();
        } catch (Exception e) {
            server.close();
            throw new IllegalStateException("测试服务器启动失败", e);
        }
        int port = ((InetSocketAddress) bindFuture.channel().localAddress()).getPort();
        return new Servers(server, port);
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    @Override
    public void close() {
        server.close();
    }
}
