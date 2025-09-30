package top.turboweb.gateway.node;

/**
 * 默认的节点
 */
public class DefaultNode implements Node {

    private final String url;

    public DefaultNode(String url) {
        this.url = url;
    }


    @Override
    public String url() {
        return url;
    }
    
}
