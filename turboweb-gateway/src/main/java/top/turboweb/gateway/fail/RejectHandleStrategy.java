package top.turboweb.gateway.fail;

import top.turboweb.loadbalance.rule.RuleDetail;

/**
 * 节点匹配失败时直接拒绝请求的策略。
 * <p>
 * 当网关无法根据 URI 匹配到任何有效路由规则时，
 * 该策略返回 {@code null}，表示当前请求不应继续处理，
 * 由上层逻辑返回错误响应（如 “Service not found”）。
 * <p>
 * 适用于以下场景：
 * <ul>
 *     <li>严格要求路由精确匹配的生产环境</li>
 *     <li>不允许请求在本地兜底或默认服务中处理</li>
 *     <li>需要快速失败以减少系统负载或避免错误行为</li>
 * </ul>
 */
public class RejectHandleStrategy implements NodeMatchFailStrategy {

    /**
     * 匹配失败时返回 {@code null}，表示拒绝处理该请求。
     *
     * @return 始终返回 null
     */
    @Override
    public RuleDetail onMatchFail() {
        return null;
    }
}
