package top.turboweb.gateway.fail;

import top.turboweb.loadbalance.rule.RuleDetail;

/**
 * 节点匹配失败后将请求降级为本地处理的策略。
 * <p>
 * 当网关无法根据请求 URI 匹配到任何服务路由时，
 * 该策略会返回一个预定义的本地 {@link RuleDetail}，
 * 使请求继续沿本地节点的处理链执行。
 * <p>
 * 适用于以下场景：
 * <ul>
 *     <li>单节点部署，希望所有请求都能在本地兜底处理</li>
 *     <li>路由动态更新期间的短暂不一致，希望避免返回错误</li>
 *     <li>业务允许本地容灾，且不存在跨服务隔离风险</li>
 * </ul>
 * <p>
 * 若不希望提供本地兜底处理，可使用 {@code NodeMatchFailStrategy.REJECT}。
 */
public class LocalHandleStrategy implements NodeMatchFailStrategy{

    /**
     * 预定义的本地 RuleDetail，用于作为匹配失败时的兜底规则。
     * <p>
     * - serviceName: "local"（表示本地容灾服务）<br>
     * - rewriteRegex: ""（不进行 URI 重写）<br>
     * - rewriteTarget: ""（不进行 URI 重写）<br>
     * - local: true（表示该规则强制在本地处理）<br>
     * - protocol: HTTP（固定为本地处理）<br>
     * - extPath: ""（无附加路径）
     */
    private static final RuleDetail RULE_DETAIL = new RuleDetail(
            "local",
            "",
            "",
            true,
            RuleDetail.Protocol.HTTP,
            ""
    );

    /**
     * 返回本地兜底处理的 RuleDetail。
     *
     * @return 始终返回预定义的本地规则，确保请求被本地节点接管处理。
     */
    @Override
    public RuleDetail onMatchFail() {
        return RULE_DETAIL;
    }
}
