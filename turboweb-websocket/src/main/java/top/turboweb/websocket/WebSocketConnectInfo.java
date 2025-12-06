package top.turboweb.websocket;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * websocket的连接信息
 */
public record WebSocketConnectInfo(String websocketPath, HttpHeaders headers) {

}
