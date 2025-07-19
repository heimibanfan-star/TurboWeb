package org.example;

import top.turboweb.core.server.BootStrapTurboWebServer;

/**
 * TODO
 */
public class App {
    public static void main(String[] args) {
        BootStrapTurboWebServer.create()
                .configServer(config -> {
                    // 设置请求体的大小
                    config.setMaxContentLength(1024 * 1024 * 10);
                    // 显示请求日志
                    config.setShowRequestLog(true);
                    // ....
                }).start();
    }
}
