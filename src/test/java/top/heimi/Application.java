package top.heimi;

import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.http.ws.WebSocketSession;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        server.setWebSocketHandler("/ws/.*", new WebSocketHandler() {

            @Override
            public void onOpen(WebSocketSession session) {
                System.out.println("onOpen:" +session.getWebSocketConnectInfo().getWebsocketPath());
                Thread.ofVirtual().start(() -> {
                    int num = 0;
                    while (num <20) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        num++;
                        session.sendMessage("你好");
                    }
                    session.close();
                });
            }

            @Override
            public void onMessage(WebSocketSession session, String message) {
                System.out.println("onMessage" + message);
                session.sendMessage("你好");
            }

            @Override
            public void onClose(WebSocketSession session) {
                System.out.println("onClose:" + session.getWebSocketConnectInfo().getWebsocketPath());
            }

            @Override
            public void onPing(WebSocketSession session) {
                System.out.println("onPing");
            }
        });
        server.addController(new UserController());
        server.start();
    }
}
