package top.turboweb.rest.uri;

/**
 * 具名端点契约：提供主机名与端口的最小读取视图。
 * <p>
 * 不可变实现应保证 {@link #getPort()} 在未指定端口时返回 {@code -1}。
 */
public interface NamedEndpoint {

    /**
     * 获取主机名。
     *
     * @return 主机名，不经过百分号解码
     */
    String getHostName();

    /**
     * 获取端口。
     *
     * @return 端口；未指定时为 {@code -1}
     */
    int getPort();
}
