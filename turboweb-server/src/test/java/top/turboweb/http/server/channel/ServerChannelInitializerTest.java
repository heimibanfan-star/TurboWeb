package top.turboweb.http.server.channel;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.utils.resource.ScopedResource;
import top.turboweb.http.server.application.Application;
import top.turboweb.http.server.config.ServerConfig;
import top.turboweb.http.server.handler.ConnectLimiterChannelHandler;
import top.turboweb.http.server.manager.ApplicationManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 连接管道装配：限流最前、编解码居中、应用最后，这是 http/1.1 路径的结构契约。
 */
class ServerChannelInitializerTest {

    /** 只留装配痕迹的应用替身，configure 被调用即在 events 里落一条 */
    private static final class MarkerApplication implements Application {
        private final List<String> events;
        private final String name;

        MarkerApplication(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
            events.add("configure:" + name);
            pipeline.addLast(new TestMarkerHandler());
        }

        @Override
        public ScopedResource getScopedResource() {
            return new ScopedResource();
        }
    }

    /**
     * 具名替身处理器：pipeline 迭代出的 handler 类型是运行时具体类，
     * indexOf 按 Class.equals 比较，匿名子类不会命中父类，因此替身必须具名
     */
    private static final class TestMarkerHandler extends ChannelInboundHandlerAdapter {
    }

    private static ServerConfig plainHttp11Config() {
        return ServerConfig.builder().maxContentLength(2048).build();
    }

    private static List<Class<?>> handlerClasses(EmbeddedChannel channel) {
        List<Class<?>> classes = new ArrayList<>();
        for (java.util.Map.Entry<String, ChannelHandler> entry : channel.pipeline()) {
            classes.add(entry.getValue().getClass());
        }
        return classes;
    }

    @Test
    @DisplayName("构造参数为null立即抛NPE")
    void constructorRejectsNulls() {
        ApplicationManager manager = new ApplicationManager();
        ConnectLimiterChannelHandler limiter = new ConnectLimiterChannelHandler(10, 1, 4);
        ServerConfig config = plainHttp11Config();

        assertThrows(NullPointerException.class, () -> new ServerChannelInitializer(null, limiter, config));
        assertThrows(NullPointerException.class, () -> new ServerChannelInitializer(manager, null, config));
        assertThrows(NullPointerException.class, () -> new ServerChannelInitializer(manager, limiter, null));
    }

    @Test
    @DisplayName("http/1.1装配顺序：限流最前、codec、aggregator、应用")
    void assemblesHttp11PipelineInOrder() {
        List<String> events = new ArrayList<>();
        ApplicationManager manager = new ApplicationManager();
        manager.addLast(new MarkerApplication("marker", events));
        ServerChannelInitializer initializer =
                new ServerChannelInitializer(manager, new ConnectLimiterChannelHandler(10, 1, 4), plainHttp11Config());

        EmbeddedChannel channel = new EmbeddedChannel(initializer);
        try {
            channel.runPendingTasks();
            channel.checkException();

            assertEquals(List.of("configure:marker"), events, "应用管理器必须在协议层之后被委托");

            List<Class<?>> classes = handlerClasses(channel);
            int limiterIndex = classes.indexOf(ConnectLimiterChannelHandler.class);
            int codecIndex = classes.indexOf(HttpServerCodec.class);
            // aggregator 与 codec 成对出现，按 configureHttp11 顺序 codec 在前
            assertTrue(limiterIndex == 0, "限流器必须是管道首位，实际管道: " + classes);
            assertTrue(codecIndex > limiterIndex, "编解码器必须排在限流之后");
            assertTrue(codecIndex >= 0 && classes.indexOf(HttpObjectAggregator.class) > codecIndex,
                    "聚合器必须排在编解码之后");
            assertTrue(classes.indexOf(TestMarkerHandler.class) > classes.indexOf(HttpObjectAggregator.class),
                    "应用处理器必须排在协议层之后");
        } finally {
            channel.close();
        }
    }
}
