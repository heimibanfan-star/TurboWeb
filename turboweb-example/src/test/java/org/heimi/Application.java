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
        new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(new NioEventLoopGroup(1))
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) throws Exception {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new SslHandler(sslContext().newEngine(channel.alloc())));
                        pipeline.addLast(new ApplicationProtocolNegotiationHandler("http/1.1") {
                            @Override
                            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                                if ("h2".equals(protocol)) {
                                    Http2FrameCodec codec = Http2FrameCodecBuilder.forServer().build();
                                    ctx.pipeline().addLast(codec);
                                    ctx.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                        @Override
                                        protected void initChannel(Channel ch) throws Exception {
                                            ch.pipeline().addLast(new Http2FrameAdaptorHandler(1024 * 1024));
                                            ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                                @Override
                                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
                                                    System.out.println(request);
                                                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                                    response.content().writeBytes(Unpooled.wrappedBuffer("hello world".getBytes()));
                                                    channelHandlerContext.writeAndFlush(response);
                                                }
                                            });
                                        }
                                    }));
                                    return;
                                }
                                throw new IllegalStateException("unknown protocol: " + protocol);
                            }
                        });
                    }
                })
                .bind(8080);
    }

    private static SslContext sslContext() throws CertificateException, SSLException {
        File certFile = new File("/home/heimi/temp/server.crt");
        File keyFile = new File("/home/heimi/temp/server.key");
        return SslContextBuilder.forServer(certFile, keyFile)
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
