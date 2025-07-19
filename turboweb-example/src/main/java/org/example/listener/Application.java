package org.example.listener;


import top.turboweb.core.server.BootStrapTurboWebServer;

public class Application {
    public static void main(String[] args) {
        BootStrapTurboWebServer.create()
                .listeners(new MyListener())
                // 禁止执行默认的监听器
                .executeDefaultListener(false)
                .start();
    }
}
