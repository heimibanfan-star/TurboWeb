package org.example;


import com.sun.management.OperatingSystemMXBean;
import io.netty.buffer.PooledByteBufAllocator;
import org.example.controller.HelloController;
import top.turboweb.core.server.StandardTurboWebServer;
import top.turboweb.core.server.TurboWebServer;
import top.turboweb.http.middleware.ServerInfoMiddleware;

import javax.management.*;

import java.lang.management.ManagementFactory;

/**
 * TODO
 */
public class Application {
        public static void main(String[] args) throws InterruptedException, MalformedObjectNameException, ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
            TurboWebServer server = new StandardTurboWebServer(Application.class, 1);
            server.controller(new HelloController(), HelloController.class);
            server.middlewares(new ServerInfoMiddleware());
            server.config(config -> {
                config.setShowRequestLog(false);
            });
//            server.disableVirtualHttpScheduler();
            server.start(8080);

        }

}

// 2221800ns 2761800ns 2180300ns
// 3055700ns 2920600ns 2742700ns
// 4219700ns 4477400ns 2295300ns
// 2430900ns 2997900ns 2708800ns
// 2488300ns 2330700ns 2567000ns

// time:10270500 time:104023000 time:360192900 time:13827012400