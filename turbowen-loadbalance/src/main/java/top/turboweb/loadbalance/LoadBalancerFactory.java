package top.turboweb.loadbalance;

import top.turboweb.loadbalance.RibbonLoadBalancer;

/**
 * 负载均衡器工厂
 */
@FunctionalInterface
public interface LoadBalancerFactory {

    LoadBalancerFactory RIBBON_LOAD_BALANCER = RibbonLoadBalancer::new;

    LoadBalancer createLoadBalancer();
}
