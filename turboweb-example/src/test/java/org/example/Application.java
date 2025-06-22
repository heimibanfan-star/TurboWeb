package org.example;


import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;

import javax.management.*;

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
        DiskOpeThreadUtils.init(4096, 2, 2);
        for (int i = 0; i < 100; i++) {
            DiskOpeThreadUtils.execute(() -> {
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