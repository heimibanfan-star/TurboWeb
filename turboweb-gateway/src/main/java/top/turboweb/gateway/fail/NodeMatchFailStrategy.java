package top.turboweb.gateway.fail;

import top.turboweb.loadbalance.rule.RuleDetail;

/**
 * 网关中服务节点匹配失败时的降级处理策略。
 * <p>
 * 在根据请求 URI 查找路由规则或服务节点时，如果未找到匹配项，
 * 网关会执行该策略以决定后续动作。
 * <p>
 * 不同策略可实现以下行为：
 * <ul>
 *     <li>直接拒绝当前请求（返回 null）</li>
 *     <li>降级为本地处理，返回一个可用的本地 RuleDetail</li>
 *     <li>自定义服务兜底行为，例如转发至默认服务或降级页面</li>
 * </ul>
 * 该接口使用策略模式，使路由失败场景具备可扩展性与可定制性。
 */
public interface NodeMatchFailStrategy {

    /**
     * 匹配失败时将请求交由本地服务处理的策略。
     * 适用于需要本地容灾或单体模式下使用。
     */
    NodeMatchFailStrategy LOCAL_HANDLE = new LocalHandleStrategy();

    /**
     * 匹配失败时直接拒绝请求的策略。
     * 适用于分布式场景下需要严格进行路由匹配的情况。
     */
    NodeMatchFailStrategy REJECT = new RejectHandleStrategy();

    /**
     * 当节点或路由匹配失败时触发的降级逻辑。
     *
     * @return 当需要继续处理请求时返回降级后的 RuleDetail；
     *         若需要直接拒绝请求则返回 null。
     */
    RuleDetail onMatchFail();
}
