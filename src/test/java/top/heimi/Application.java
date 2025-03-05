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
        server.setWebSocketHandler("/ws", new WebSocketHandler() {

            @Override
            public void onOpen(WebSocketSession session) {
                System.out.println("onOpen");
            }

            @Override
            public void onMessage(WebSocketSession session, String message) {
                System.out.println("onMessage" + message);
                session.sendMessage("你好");
            }

            @Override
            public void onClose(WebSocketSession session) {
                System.out.println("onClose");
            }

            @Override
            public void onPing(WebSocketSession session) {

            }
        });
        server.addController(new UserController());
        server.start();
    }
}
