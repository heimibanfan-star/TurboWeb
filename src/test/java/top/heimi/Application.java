package top.heimi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
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
        server.start(8080);
    }
}
