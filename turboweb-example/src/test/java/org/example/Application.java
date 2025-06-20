package org.example;


import org.example.controller.UserController;
import top.turboweb.core.server.BootStrapTurboWebServer;

import javax.management.*;
import java.nio.charset.StandardCharsets;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        BootStrapTurboWebServer.create(Application.class)
                .http()
                .controller(new UserController())
                .and()
                .configServer(c -> {
                    c.setShowRequestLog(false);
                })
                .configClient(c -> {
                    c.setCharset(StandardCharsets.UTF_8);
                })
                .start(8080);
    }

}