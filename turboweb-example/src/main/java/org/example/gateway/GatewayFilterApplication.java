package org.example.gateway;


import io.netty.handler.codec.http.FullHttpRequest;
import reactor.core.publisher.Mono;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.gateway.filter.GatewayFilter;
import top.turboweb.gateway.filter.ResponseHelper;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.loadbalance.rule.NodeRuleManager;

public class GatewayFilterApplication {
    public static void main(String[] args) {
        GatewayChannelHandler<Mono<Boolean>> handler = GatewayChannelHandler.createAsync();

        handler.addFilter((request, responseHelper) -> {
            System.out.println("GatewayFilter");
            // 设置响应的内容
            responseHelper.writeHtml("鉴权失败");
            return Mono.just(false);
        });


        NodeRuleManager ruleManager = new NodeRuleManager();
        ruleManager.addRule("/**", "http://local");
        handler.setRule(ruleManager);

        BootStrapTurboWebServer.create()
                .http()
                .middleware(new Middleware() {
                    @Override
                    public Object invoke(HttpContext ctx) {
                        return "Hello World";
                    }
                })
                .and()
                .gatewayHandler(handler)
                .start();
    }
}
