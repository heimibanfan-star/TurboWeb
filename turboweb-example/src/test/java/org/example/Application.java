package org.example;


import org.example.controller.HelloController;
import org.example.controller.UserController;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.ServerInfoMiddleware;

import javax.management.*;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        BootStrapTurboWebServer.create(Application.class)
                .http()
                .controller(new UserController())
                .and()
                .config(config -> {
                    config.setShowRequestLog(false);
                })
                .start(8080);
    }

}