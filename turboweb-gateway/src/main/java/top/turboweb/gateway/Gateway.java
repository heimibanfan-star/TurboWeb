package top.turboweb.gateway;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import reactor.netty.http.client.HttpClient;


/**
 * 网关接口
 */
public interface Gateway {

    /**
     * 添加服务的节点
     *
     * @param prefix 前缀
     * @param urls 路由
     */
    void addServerNode(String prefix, String... urls);

    /**
     * 设置 HttpClient 实例，仅允许调用一次。
     * 框架启动时会自动注入默认 HttpClient，但如果用户在此之前已设置，
     * 框架将不会覆盖用户配置。
     *
     * @param httpClient HttpClient
     */
    void setHttpClient(HttpClient httpClient);

    /**
     * 匹配节点的主机地址
     *
     * @param uri 请求的路径
     * @return 匹配节点
     */
    String matchNode(String uri);

    /**
     * 请求的转发
     *
     * @param url 主机地址
     * @param fullHttpRequest 发送请求
     * @param channel 管道
     */
    void forwardRequest(String url, FullHttpRequest fullHttpRequest, Channel channel);
}
