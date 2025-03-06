package org.turbo.web.core.http.ws;

/**
 * websocket的连接信息
 */
public class WebSocketConnectInfo {

    private String websocketPath;

    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    public String getWebsocketPath() {
        return websocketPath;
    }
}
