package top.turboweb.loadbalance;

import top.turboweb.loadbalance.node.Node;

import java.util.Map;
import java.util.Set;

/**
 * 负载均衡接口。
 *
 * <p>用于管理服务及其节点，并根据负载均衡策略选择可用节点。
 * 实现类可以提供不同的负载均衡算法，如轮询、随机、加权、最小连接等。
 *
 * <p>典型使用流程：
 * <ul>
 *     <li>添加服务及节点：{@link #addServices(String, String...)}</li>
 *     <li>根据服务名获取负载均衡后的节点：{@link #loadBalance(String)}</li>
 *     <li>删除或重置服务节点：{@link #removeServices(String)}, {@link #removeServiceNode(String, String)}, {@link #resetServiceNodes(Map)}</li>
 * </ul>
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
