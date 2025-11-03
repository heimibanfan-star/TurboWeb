package org.example.gateway;

import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.loadbalance.breaker.Breaker;
import top.turboweb.loadbalance.breaker.DefaultBreaker;


public class BreakerApplication {
    public static void main(String[] args) {
        DefaultBreaker breaker = new DefaultBreaker(5000);
        // 设置被判断为失败的状态码
        breaker.setFailStatusCode(500);
        // 设置时间窗口内熔断失败的阈值，该例子的意思是10s内，失败次数超过200，则触发熔断
        breaker.setFailThreshold(200);
        breaker.setFailWindowTTL(10000);
        // 设置尝试恢复触发时机，当熔断之后超过5s,转化为半开尝试恢复
        breaker.setRecoverTime(5000);
        // 设置处于半开状态为10s，尝试恢复成功率超过80%，则恢复为正常状态
        breaker.setRecoverWindowTTL(10000);
        breaker.setRecoverPercent(0.8);

        // 将断路器设置进入网关
        GatewayChannelHandler<Boolean> gatewayChannelHandler = GatewayChannelHandler.create(breaker);
    }
}
