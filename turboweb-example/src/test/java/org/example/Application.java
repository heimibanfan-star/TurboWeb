package org.example;


import org.example.controller.HelloController;
import org.example.interceptors.OneInterceptor;
import org.example.interceptors.ThreeInterceptor;
import org.example.interceptors.TwoInterceptor;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.interceptor.InterceptorManager;

import javax.management.*;
import java.util.List;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        InterceptorManager interceptorManager = new InterceptorManager();
        interceptorManager.addInterceptionHandler("/hello/**", new OneInterceptor());
        interceptorManager.addInterceptionHandler("/hello/*", new TwoInterceptor());
        interceptorManager.addInterceptionHandler("/hello/**", new ThreeInterceptor());
        BootStrapTurboWebServer.create(Application.class)
                .http()
                .controller(new HelloController())
                .middleware(interceptorManager)
                .cors(config -> {
                    config.setAllowedMethods(List.of("POST"));
                })
                .and()
                .start(8080);
    }

}