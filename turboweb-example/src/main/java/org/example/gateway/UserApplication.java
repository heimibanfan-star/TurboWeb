package org.example.gateway;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.loadbalance.rule.NodeRuleManager;

public class UserApplication {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new UserController());

        // 创建嵌入式网关
        GatewayChannelHandler<Boolean> gateway = GatewayChannelHandler.create();
        // 注册服务节点
        gateway.addService("orderService", "localhost:8081");
        // 配置映射规则
        NodeRuleManager ruleManager = new NodeRuleManager();
        ruleManager.addRule("/order/**", "http://orderService");
        // 注册本地节点
        ruleManager.addRule("/api/user/**", "http://local", "/api", "");
        gateway.setRule(ruleManager);

        // 启动服务器并注册网关
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .gatewayHandler(gateway)
                .start(8080);
    }
}
