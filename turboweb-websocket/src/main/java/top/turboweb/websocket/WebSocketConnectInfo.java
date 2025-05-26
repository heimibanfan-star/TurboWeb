package top.turboweb.websocket;

/**
 * websocket的连接信息
 */
public class WebSocketConnectInfo {

    private final String websocketPath;

    public WebSocketConnectInfo(String websocketPath) {
        this.websocketPath = websocketPath;
    }


    public String getWebsocketPath() {
        return websocketPath;
    }
}
