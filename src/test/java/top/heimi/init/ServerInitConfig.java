package top.heimi.init;

import io.netty.bootstrap.ServerBootstrap;
import org.turbo.web.core.init.TurboServerInit;

/**
 * TODO
 */
public class ServerInitConfig implements TurboServerInit {
    @Override
    public void beforeTurboServerInit(ServerBootstrap serverBootstrap) {
        System.out.println("""
            这个是对serverBootStrap初始化之前调用，
            这时的serverBootStrap是刚创建的对象
            """);
    }

    @Override
    public void afterTurboServerInit(ServerBootstrap serverBootstrap) {
        System.out.println("""
            这是在serverBootStrap调用初始化方法之后调用，
            这时eventLoop和系统内置的handler被设置完成。
            """);
    }

    @Override
    public void afterTurboServerStart() {
        System.out.println("""
            这是在服务器启动之后调用的方法
            """);
    }
}
