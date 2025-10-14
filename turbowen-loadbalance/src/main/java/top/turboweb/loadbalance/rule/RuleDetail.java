package top.turboweb.loadbalance.rule;

/**
 * 规则的详细信息
 */
public record RuleDetail(
        // 服务名称
        String serviceName,
        // 重写正则
        String rewriteRegex,
        // 重写目标
        String rewriteTarget,
        // 是否是本地节点
        boolean local,
        // 协议
        String protocol,
        // 附加路径
        String extPath
) {
}
