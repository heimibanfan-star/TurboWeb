package org.heimi;

import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.gateway.GatewayChannelHandler;
import top.turboweb.gateway.fail.NodeMatchFailStrategy;
import top.turboweb.http.middleware.router.AnnoRouterManager;
import top.turboweb.loadbalance.breaker.DefaultBreaker;
import top.turboweb.loadbalance.rule.NodeRuleManager;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.security.cert.CertificateException;

public class Application {
    public static void main(String[] args) throws CertificateException, IOException {

        GatewayChannelHandler<Boolean> gatewayChannelHandler = GatewayChannelHandler.create(new DefaultBreaker(), NodeMatchFailStrategy.REJECT);
        gatewayChannelHandler.setRule(new NodeRuleManager());
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new HelloController());
        BootStrapTurboWebServer.create(1)
                .http()
                .routerManager(routerManager)
                .and()
                .gatewayHandler(gatewayChannelHandler)
                .start();
//        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
//        File certificate = selfSignedCertificate.certificate();
//        File privateKey = selfSignedCertificate.privateKey();


    }

    private static SslContext sslContext(boolean h2) throws CertificateException, SSLException {
        String[] protocols;
        if (h2) {
            protocols = new String[]{ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1};
        } else {
            protocols = new String[]{ApplicationProtocolNames.HTTP_1_1};
        }
        SelfSignedCertificate cert = new SelfSignedCertificate();
        return SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .protocols("TLSv1.3", "TLSv1.2")
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        protocols))
                .build();
    }
}
