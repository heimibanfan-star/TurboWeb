package top.turboweb.loadbalance.rule;

/**
 * 仅支持http相关协议的节点管理器
 */
public class HttpOnlyNodeRuleManager extends NodeRuleManager {

    @Override
    public NodeRuleManager addRule(String pattern, String serviceExpression, String rewRegix, String rewTar) {
        if (serviceExpression.startsWith("ws") || serviceExpression.startsWith("wss")) {
            throw new IllegalArgumentException("The serviceExpression cannot be websocket protocol:" + serviceExpression);
        }
        return super.addRule(pattern, serviceExpression, rewRegix, rewTar);
    }
}
