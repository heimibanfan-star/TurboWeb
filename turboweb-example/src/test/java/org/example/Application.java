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

// 2221800ns 2761800ns 2180300ns
// 3055700ns 2920600ns 2742700ns
// 4219700ns 4477400ns 2295300ns
// 2430900ns 2997900ns 2708800ns
// 2488300ns 2330700ns 2567000ns

// time:10270500 time:104023000 time:360192900 time:13827012400