package top.turboweb.loadbalance.rule;

/**
 * 服务映射规则管理器接口。
 *
 * <p>用于根据请求路径获取对应的服务信息，包括本地服务和远程服务。
 * 实现类应支持路径模式匹配，并可在规则启用后禁止修改。
 *
 * <p>通常用于网关、负载均衡或服务路由模块，根据路径规则决定请求转发目标。
 */
public interface RuleManager {

    /** 本地服务标识 */
    String LOCAL_SERVICE = "local";


    /**
     * 根据请求路径获取本地服务。
     *
     * @param path 请求路径
     * @return 匹配的本地服务规则详情，如果不存在返回 {@code null}
     */
    RuleDetail getLocalService(String path);

    /**
     * 根据请求路径获取远程服务。
     *
     * @param path 请求路径
     * @return 匹配的远程服务规则详情，如果不存在返回 {@code null}
     */
    RuleDetail getRemoteService(String path);

    /**
     * 根据请求路径获取服务（本地或远程）。
     *
     * <p>实现类可以根据策略决定本地服务是否优先返回。
     *
     * @param path 请求路径
     * @return 匹配的服务规则详情，如果不存在返回 {@code null}
     */
    RuleDetail getService(String path);

    /**
     * 启用规则管理器。
     *
     * <p>首次调用会将规则状态置为已启用并返回 {@code true}，
     * 后续调用返回 {@code false}。
     *
     * @return {@code true} 表示首次启用成功，{@code false} 表示规则已启用
     */
    boolean used();
}
