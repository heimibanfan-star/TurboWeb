package org.example.listener;


import top.turboweb.core.listener.TurboWebListener;

public class MyListener implements TurboWebListener {
    @Override
    public void beforeServerInit() {
        System.out.println("在服务器进行初始化之间会触发");
    }

    @Override
    public void afterServerStart() {
        System.out.println("在服务器启动之后会触发");
    }
}
