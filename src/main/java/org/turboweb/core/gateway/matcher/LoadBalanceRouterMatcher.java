package org.turboweb.core.gateway.matcher;

/**
 * 负载均衡路由匹配器
 */
public interface LoadBalanceRouterMatcher {

    /**
     * 匹配服务节点
     *
     * @param uri 请求的uri
     * @return 可以处理请求的uri地址
     */
    String matchNode(String uri);

    /**
     * 添加服务的节点
     *
     * @param prefix 处理的前缀
     * @param urls 服务的地址
     */
    void addServiceNode(String prefix, String... urls);
}
