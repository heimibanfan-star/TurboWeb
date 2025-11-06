package top.turboweb.loadbalance.node;

/**
 * 服务节点接口。
 *
 * <p>每个节点代表一个可用的服务实例，通常包含节点的访问地址信息。
 * 负载均衡器会根据 {@link Node} 实例进行调度和选择。
 */
public interface Node {

    /**
     * 获取节点的访问地址。
     *
     * @return 节点的 URL 或地址，例如 "http://127.0.0.1:8080"
     */
    String url();

}
