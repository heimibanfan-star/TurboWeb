package org.example.gateway;


import io.netty.buffer.ByteBuf;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.websocket.AbstractWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

public class WsApplication {
    public static void main(String[] args) {
        BootStrapTurboWebServer.create()
                .protocol()
                .websocket("/ws", new AbstractWebSocketHandler() {

                    @Override
                    public void onOpen(WebSocketSession session) {
                        session.sendText("管道建立成功");
                    }

                    @Override
                    public void onText(WebSocketSession session, String content) {
                    }

                    @Override
                    public void onBinary(WebSocketSession session, ByteBuf content) {

                    }
                })
                .and()
                .start(8081);
    }
}
