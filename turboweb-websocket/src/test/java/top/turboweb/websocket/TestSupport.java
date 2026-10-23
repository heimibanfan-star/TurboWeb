package top.turboweb.websocket;

import top.turboweb.concurrent.thread.MicroTaskExecutor;

import java.lang.reflect.Field;

/**
 * 测试用反射访问器：主码只暴露必要的装配入口时，用来观测装配结果。
 */
final class TestSupport {

    private TestSupport() {
    }

    /**
     * 取出初始化 handler 内部的分发处理器注入的微任务执行器。
     */
    static MicroTaskExecutor executorOf(WebSocketInitChannelHandler initHandler) throws ReflectiveOperationException {
        Field dispatcherField = WebSocketInitChannelHandler.class.getDeclaredField("webSocketDispatcherHandler");
        dispatcherField.setAccessible(true);
        Object dispatcher = dispatcherField.get(initHandler);

        Field executorField = WebSocketChannelHandler.class.getDeclaredField("microTaskExecutor");
        executorField.setAccessible(true);
        return (MicroTaskExecutor) executorField.get(dispatcher);
    }
}
