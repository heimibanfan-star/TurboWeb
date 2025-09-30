package org.heimi;

import io.netty.buffer.ByteBuf;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.DefaultGateway;
import top.turboweb.gateway.Gateway;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;
import top.turboweb.http.response.SseResponse;
import top.turboweb.websocket.AbstractWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

/**
 * TODO
 */
public class OrderApplication {
    public static void main(String[] args) {
        LambdaRouterManager routerManager = new LambdaRouterManager();
        routerManager.addGroup(new LambdaRouterGroup() {
            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/order", (ctx) -> "order");
                register.get("/order/sse", ctx -> {
                    SseResponse sseResponse = ctx.createSseResponse();
                    sseResponse.setSseCallback(session -> {
                        for (int i = 0; i < 10; i++) {
                            session.send("hello world " + i);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        session.close();
                    });
                    return sseResponse;
                });
            }
        });

        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .protocol()
                .websocket("/ws", new AbstractWebSocketHandler() {

                    @Override
                    public void onOpen(WebSocketSession session) {
                        System.out.println("连接成功"   );
                    }

                    @Override
                    public void onText(WebSocketSession session, String content) {
                        System.out.println("收到文本消息: " + content);
                        session.sendText("收到消息: " + content);
                    }

                    @Override
                    public void onBinary(WebSocketSession session, ByteBuf content) {
                        System.out.println("收到二进制消息: " + content);
                    }

                    @Override
                    public void onClose(WebSocketSession session) {
                        System.out.println("连接关闭");
                    }
                })
                .and()
                .start(8081);
    }
}
