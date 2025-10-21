package top.turboweb.loadbalance;


/**
 * 负载均衡器工厂接口。
 *
 * <p>用于创建 {@link LoadBalancer} 实例，提供统一的实例化入口。
 * 通过工厂模式，用户可以灵活选择不同的负载均衡策略实现。
 *
 * <p>示例：
 * <pre>{@code
 * LoadBalancer lb = LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer();
 * }</pre>
 *
 * <p>该接口为函数式接口，可使用 Lambda 表达式或方法引用创建实例。
 */
@FunctionalInterface
public interface LoadBalancerFactory {

    /**
     * 默认 Ribbon 负载均衡器工厂实例。
     */
    LoadBalancerFactory RIBBON_LOAD_BALANCER = RibbonLoadBalancer::new;

    /**
     * 创建一个 {@link LoadBalancer} 实例。
     *
     * @return 新的负载均衡器实例
     */
    LoadBalancer createLoadBalancer();
}
