package org.example.limiter;

import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.limiter.PathLimiter;

public class Application {
    public static void main(String[] args) {
//        BootStrapTurboWebServer.create()
//                .configServer(config -> {
//                    // 设置最大连接数
//                    config.setMaxConnections(5000);
//                    // 设置CPU核心数
//                    config.setCpuNum(8);
//                    // 开启调度器限流
//                    config.setEnableHttpSchedulerLimit(true);
//                    // 设置可以并发处理的请求数
//                    config.setHttpSchedulerLimitCount(1000);
//                    // 设置最大的挂起线程数
//                    config.setHttpSchedulerLimitCacheThread(2000);
//                    // 设置线程挂起的超时时间
//                    config.setHttpSchedulerLimitTimeout(1000);
//                })
//                .start();
        // 创建路径限流器
        PathLimiter pathLimiter = new PathLimiter();
        // 添加规则
        pathLimiter.addRule("/user/**", 10, 1);
        BootStrapTurboWebServer.create()
                // 注册限流器
                .http().middleware(pathLimiter)
                .and().start();
    }
}
