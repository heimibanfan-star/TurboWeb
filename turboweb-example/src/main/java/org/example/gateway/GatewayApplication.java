package org.example.gateway;


import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.loadbalance.rule.NodeRuleManager;

public class GatewayApplication {
    public static void main(String[] args) {
        GatewayChannelHandler<Boolean> gatewayHandler = GatewayChannelHandler.create();
        // 设置远程服务节点
        gatewayHandler.addService("wsService", "localhost:8081");
        // 添加映射规则
        NodeRuleManager ruleManager = new NodeRuleManager();
        ruleManager.addRule("/ws/**", "ws://wsService");
        gatewayHandler.setRule(ruleManager);

        BootStrapTurboWebServer.create()
                .gatewayHandler(gatewayHandler)
                .start(8080);
    }
}
