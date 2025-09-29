package top.turboweb.gateway.loadbalance;

/**
 * 负载均衡器工厂
 */
@FunctionalInterface
public interface LoadBalancerFactory {

    LoadBalancerFactory RIBBON_LOAD_BALANCER = RibbonLoadBalancer::new;

    LoadBalancer createLoadBalancer();
}
