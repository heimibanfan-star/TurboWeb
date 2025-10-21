package top.turboweb.loadbalance.node;

/**
 * 默认的服务节点实现。
 *
 * <p>该类实现了 {@link Node} 接口，表示一个具体的服务实例节点，
 * 包含节点的访问地址信息。
 */
public class DefaultNode implements Node {

    /**
     * 节点访问地址，例如 "http://127.0.0.1:8080"
     */
    private final String url;

    /**
     * 构造默认节点。
     *
     * @param url 节点访问地址
     */
    public DefaultNode(String url) {
        this.url = url;
    }

    /**
     * 获取节点的访问地址。
     *
     * @return 节点 URL
     */
    @Override
    public String url() {
        return url;
    }
    
}
