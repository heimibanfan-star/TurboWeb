package org.example;


import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.example.controller.HelloController;
import org.example.interceptors.OneInterceptor;
import org.example.interceptors.ThreeInterceptor;
import org.example.interceptors.TwoInterceptor;
import top.turboweb.commons.struct.trie.PatternPathTrie;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.interceptor.InterceptorMiddleware;

import javax.management.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        InterceptorMiddleware interceptorMiddleware = new InterceptorMiddleware();
        interceptorMiddleware.addInterceptionHandler("/hello/**", new OneInterceptor());
        interceptorMiddleware.addInterceptionHandler("/hello/*", new TwoInterceptor());
        interceptorMiddleware.addInterceptionHandler("/hello/**", new ThreeInterceptor());
        BootStrapTurboWebServer.create(Application.class)
                .http()
                .controller(new HelloController())
                .middleware(interceptorMiddleware)
                .cors(config -> {
                    config.setAllowedMethods(List.of("POST"));
                })
                .and()
                .start(8080);
    }

}