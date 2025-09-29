package top.turboweb.gateway.node;

/**
 * 默认的节点
 */
public class DefaultNode implements Node {

    private final String url;
    private final boolean local;

    public DefaultNode(String url, boolean local) {
        this.url = url;
        this.local = local;
    }


    @Override
    public String url() {
        return url;
    }

    @Override
    public boolean isLocal() {
        return local;
    }
}
