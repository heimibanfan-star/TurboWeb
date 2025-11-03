package org.heimi;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import top.turboweb.core.handler.Http2FrameAdaptorHandler;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.MixedMiddleware;
import top.turboweb.http.middleware.TypedSkipMiddleware;
import top.turboweb.http.middleware.router.AnnoRouterManager;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.cert.CertificateException;

public class Application {
    public static void main(String[] args) throws CertificateException, IOException {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new HelloController());
        BootStrapTurboWebServer.create(4)
                .http()
                .routerManager(routerManager)
                .and()
                .ssl(sslContext())
                .enableHttp2()
                .configServer(config -> {
                    config.setShowRequestLog(false);
                })
                .start();
    }

    private static SslContext sslContext() throws CertificateException, SSLException {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        return SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .protocols("TLSv1.3", "TLSv1.2")
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();
    }
}
