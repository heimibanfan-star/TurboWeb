package top.turboweb.gateway.client;

import io.netty.channel.EventLoopGroup;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.util.function.Function;

/**
 * 反应式HTTP客户端的创建工厂
 */
public class ReactorHttpClientFactory {

    /**
     * 创建一个反应式HTTP客户端
     *
     * @param group       线程组
     * @param function    连接提供者构建函数
     * @return HTTP客户端
     */
    public static HttpClient createHttpClient(EventLoopGroup group, Function<ConnectionProvider.Builder, ConnectionProvider.Builder> function) {
        ConnectionProvider.Builder builder = ConnectionProvider.builder("httpClient");
        ConnectionProvider provider = function.apply(builder).build();
        return HttpClient.create(provider).runOn(group);
    }
}
