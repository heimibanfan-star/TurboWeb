package org.example;


import org.example.controller.HelloController;
import org.example.controller.UserController;
import top.turboweb.commons.utils.thread.BackupThreadUtils;
import top.turboweb.core.server.BootStrapTurboWebServer;

import javax.management.*;
import java.nio.charset.StandardCharsets;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) throws InterruptedException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
//        BootStrapTurboWebServer.create(Application.class)
//                .http()
//                .controller(new HelloController())
//                .and()
//                .configServer(c -> {
//                    c.setShowRequestLog(false);
//                })
//                .configClient(c -> {
//                    c.setCharset(StandardCharsets.UTF_8);
//                })
//                .start(8080);
        BackupThreadUtils.init(4096, 2, 2);
        for (int i = 0; i < 100; i++) {
            BackupThreadUtils.execute(() -> {
                System.out.println(Thread.currentThread().getName());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

}