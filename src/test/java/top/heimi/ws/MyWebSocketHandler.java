package top.heimi.ws;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.*;
import org.turbo.web.core.http.ws.AbstractWebSocketHandler;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.http.ws.WebSocketSession;

/**
 * websocket处理器
 */
public class MyWebSocketHandler extends AbstractWebSocketHandler {

    @Override
    public void onText(WebSocketSession session, String content) {
        System.out.println(content);
    }

    @Override
    public void onBinary(WebSocketSession session, ByteBuf content) {
        System.out.println("二进制帧");
    }
}
