package top.turboweb.loadbalance.rule;

/**
 * HTTP/HTTPS 专用的节点规则管理器。
 *
 * <p>该类继承自 {@link NodeRuleManager}，用于管理节点路由规则，但只允许
 * HTTP 和 HTTPS 协议的服务。任何 WebSocket 协议（ws/wss）都会被拒绝。
 *
 * <p>适用于只处理 HTTP/HTTPS 服务请求的场景，防止误将 WebSocket 服务添加到节点规则中。
 */
public class HttpOnlyNodeRuleManager extends NodeRuleManager {

    /**
     * 添加节点规则。
     *
     * <p>重写父类方法，在添加规则前校验服务协议是否为 HTTP/HTTPS，如果是
     * WebSocket 协议（ws/wss）则抛出 {@link IllegalArgumentException}。
     *
     * @param pattern           请求路径匹配模式
     * @param serviceExpression 服务表达式，例如 "http://serviceName"
     * @param rewRegix          重写路径正则（可为 null 或空字符串）
     * @param rewTar            重写目标路径（可为 null 或空字符串）
     * @return 当前 {@link NodeRuleManager} 实例
     * @throws IllegalArgumentException 如果服务表达式使用 ws 或 wss 协议
     */
    @Override
    public NodeRuleManager addRule(String pattern, String serviceExpression, String rewRegix, String rewTar) {
        if (serviceExpression.startsWith("ws") || serviceExpression.startsWith("wss")) {
            throw new IllegalArgumentException("The serviceExpression cannot be websocket protocol:" + serviceExpression);
        }
        return super.addRule(pattern, serviceExpression, rewRegix, rewTar);
    }
}
