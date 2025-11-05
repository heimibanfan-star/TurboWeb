package org.example.https;


import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

public class HttpsApplication {
    public static void main(String[] args) throws CertificateException, SSLException {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        SslContext sslContext = SslContextBuilder.forServer(
                // 配置证书
                certificate.certificate(),
                // 配置私钥
                certificate.privateKey()
        ).build();

        BootStrapTurboWebServer.create()
                .http()
                .middleware(new Middleware() {
                    @Override
                    public Object invoke(HttpContext ctx) {
                        return "hello world";
                    }
                })
                .and()
                // 在此配置sslContext即可完成https的配置
                .ssl(sslContext)
                // 启用http2
                .enableHttp2()
                .start();
    }
}
