package org.heimi;

import reactor.core.publisher.Mono;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.loadbalance.rule.NodeRuleManager;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO
 */
public class UserApplication {

    AtomicInteger integer = new AtomicInteger();
    public static void main(String[] args) {
        LambdaRouterManager routerManager = new LambdaRouterManager();
        routerManager.addGroup(new LambdaRouterGroup() {
            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/user", (ctx) -> "User");
            }
        });
        GatewayChannelHandler<Mono<Boolean>> gatewayChannelHandler = GatewayChannelHandler.createAsync();
        gatewayChannelHandler
                .addFilter((request, responseHelper) -> Mono.create(sink -> {
                    Thread.ofVirtual().start(() -> {
                        System.out.println(request.uri());
                        System.out.println("filter-----1");
                        try {
                            TimeUnit.MILLISECONDS.sleep(50);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        sink.success(true);
                    });
                }))
                .addFilter((request, responseHelper) -> Mono.create(sink -> {
                    Thread.ofVirtual().start(() -> {
                        System.out.println(request.uri());
                        System.out.println("filter-----2");
                        try {
                            TimeUnit.MILLISECONDS.sleep(50);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        sink.success(true);
                    });
                }))
        ;
        gatewayChannelHandler.addService("orderService", "localhost:8081");
        NodeRuleManager rule = new NodeRuleManager();
        rule.addRule("/order/**", "http://orderService");
        rule.addRule("/ws/**", "ws://orderService");
        gatewayChannelHandler.setRule(rule);
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .gatewayHandler(gatewayChannelHandler)
                .start(8080);
    }
}
