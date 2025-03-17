package top.heimi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.turbo.web.core.http.middleware.FreemarkerTemplateMiddleware;
import org.turbo.web.core.http.middleware.StaticResourceMiddleware;
import org.turbo.web.core.http.ws.WebSocketHandler;
import org.turbo.web.core.http.ws.WebSocketSession;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import top.heimi.controller.HelloController;
import top.heimi.middleware.AuthMiddleware;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class);
        server.addController(new HelloController());
        server.addMiddleware(
            new StaticResourceMiddleware(),
            new FreemarkerTemplateMiddleware(),
            new AuthMiddleware()
        );
        server.setWebSocketHandler("/ws/.*", new WebSocketHandler() {

            @Override
            public void onOpen(WebSocketSession session) {
                System.out.println("open");
                Thread.ofVirtual().start(() -> {
                    int num = 0;
                    while (num < 10) {
                        try {
                            Thread.sleep(1000);
                            session.sendMessage("hello world + " + num);
                            num++;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                    session.close();
                });
            }

            @Override
            public void onMessage(WebSocketSession session, String message) {
                System.out.println("receive message: " + message);
            }

            @Override
            public void onClose(WebSocketSession session) {
                System.out.println("close");
            }
        });
        server.start(8080);
    }
}
