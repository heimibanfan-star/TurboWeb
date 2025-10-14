package top.turboweb.loadbalance;

import top.turboweb.loadbalance.node.Node;

import java.util.Map;
import java.util.Set;

/**
 * 负载均衡接口
 */
public interface LoadBalancer {

    /**
     * 添加服务
     *
     * @param serviceName 服务名
     * @param urls        服务地址
     */
    void addServices(String serviceName, String... urls);

    /**
     * 添加服务
     *
     * @param serviceName 服务名
     * @param urls        服务地址
     */
    void addServices(String serviceName, Set<String> urls);


    /**
     * 删除服务的全部节点
     *
     * @param serviceName 服务名
     */
    void removeServices(String serviceName);

    /**
     * 删除一个服务节点
     *
     * @param serviceName 服务名
     * @param nodeUrl     服务节点地址
     */
    void removeServiceNode(String serviceName, String nodeUrl);

    /**
     * 负载均衡
     *
     * @param serviceName 服务名
     * @return 服务节点
     */
    Node loadBalance(String serviceName);

    /**
     * 重置服务节点
     *
     * @param servicesNodes 服务节点
     */
    void resetServiceNodes(Map<String, Set<String>> servicesNodes);
}
