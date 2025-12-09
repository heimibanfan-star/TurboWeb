package org.example.httpclient;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.LambdaRouterGroup;
import top.turboweb.http.middleware.router.LambdaRouterManager;

import javax.net.ssl.SSLException;
import java.io.File;
import java.security.cert.CertificateException;


public class HttpServerApplication {

    public static void main(String[] args) throws CertificateException, SSLException {
        LambdaRouterManager manager = new LambdaRouterManager();
        manager.addGroup(new LambdaRouterGroup() {
            @Override
            protected void registerRoute(RouterRegister register) {
                register.get("/hello", ctx -> "Hello World");
                register.get("/user", ctx -> new User("Tom", 18));
                register.get("/query", ctx -> ctx.loadQuery(User.class));
                register.post("/json", ctx -> ctx.loadJson(User.class));
                register.post("/form", ctx -> ctx.loadForm(User.class));
                register.get("/interceptor", ctx -> {
                    HttpHeaders headers = ctx.getRequest().headers();
                    return headers.get("Authorization");
                });
            }
        });

        File cert = new File("E://temp/server.cert");
        File key = new File("E://temp/server.key");
        SslContext sslContext = SslContextBuilder.forServer(cert, key).build();

        BootStrapTurboWebServer.create()
                .http()
                .routerManager(manager)
                .and()
                .ssl(sslContext)
                .start(8080);
    }
}
