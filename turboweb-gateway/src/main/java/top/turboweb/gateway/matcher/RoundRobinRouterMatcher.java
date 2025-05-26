package top.turboweb.gateway.matcher;

import top.turboweb.commons.exception.TurboGatewayException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 轮训负载均衡匹配器
 */
public class RoundRobinRouterMatcher implements LoadBalanceRouterMatcher {

    private final Map<String, AtomicReference<ServiceNode>> nodes = new ConcurrentHashMap<>();


    @Override
    public String matchNode(String uri) {
        // 路由匹配
        for (Map.Entry<String, AtomicReference<ServiceNode>> entry : nodes.entrySet()) {
            if (uri.startsWith(entry.getKey())) {
                return entry.getValue().getAndUpdate(serviceNode -> serviceNode.next).url;
            }
        }
        return null;
    }

    @Override
    public void addServiceNode(String prefix, String... urls) {
        if (urls.length == 0) {
            throw new TurboGatewayException("节点的路由不能为空:" + prefix);
        }
        ServiceNode head = new ServiceNode();
        head.url = urls[0];
        head.next = head;
        ServiceNode ptr = head;
        for (int index = 1; index < urls.length; index++) {
            ServiceNode node = new ServiceNode();
            node.url = urls[index];
            node.next = ptr.next;
            ptr.next = node;
            ptr = node;
        }
        nodes.put(prefix, new AtomicReference<>(head));
    }

    /**
     * 服务节点的循环链表
     */
    static class ServiceNode {
        String url;
        ServiceNode next;
    }
}
