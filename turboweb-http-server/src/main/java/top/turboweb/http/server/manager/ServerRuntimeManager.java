package top.turboweb.http.server.manager;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.server.runtime.ServerRuntime;

import java.util.LinkedList;
import java.util.Objects;

/**
 * 运行时管理器：应用级处理器在连接管道上的装配入口。
 * <p>
 * 本类只管应用——持有 {@link #addFirst(ServerRuntime)} / {@link #addLast(ServerRuntime)} 注册的应用列表，
 * 并在 {@link #configure(ChannelPipeline)} 时按注册顺序把各应用追加到管道尾部。连接限流、ssl、
 * http编解码与http2等服务器级传输处理器的装配不属于本类，由
 * {@code top.turboweb.http.server.channel.ServerChannelInitializer} 在应用之前完成，
 * 因此本类不依赖 {@link top.turboweb.http.server.config.ServerConfig}。
 * <p>
 * 应用的注册须在服务器启动前确定：{@code configure} 会被多个worker线程并发调用，
 * 启动后再修改应用列表存在并发风险。
 * <p>
 * 本类同时是应用层的关闭入口：{@link #close()} 按注册顺序逐个调用应用的close，
 * 由各应用自己释放装配期创建的资源（线程池、中间件销毁等），通常在服务器关闭时触发。
 *
 * <pre>{@code
 * ServerRuntimeManager manager = new ServerRuntimeManager()
 *         .addLast(httpRuntime)
 *         .addLast(webSocketRuntime);
 * // 每条新连接协议层就绪后由 ServerChannelInitializer 调用：manager.configure(pipeline);
 * }</pre>
 */
public class ServerRuntimeManager implements AutoCloseable {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ServerRuntimeManager.class);

    /**
     * 已注册的应用列表，装配顺序即列表顺序（addFirst插队头、addLast排队尾）
     */
    private final LinkedList<ServerRuntime> runtimes = new LinkedList<>();

    /**
     * 将应用插到应用列表头部（装配时最先执行），应用为null立即抛出 {@link NullPointerException}
     */
    public ServerRuntimeManager addFirst(ServerRuntime runtime) {
        Objects.requireNonNull(runtime, "The runtime must not be null");
        this.runtimes.addFirst(runtime);
        return this;
    }

    /**
     * 将应用追加到应用列表尾部（装配时最后执行），应用为null立即抛出 {@link NullPointerException}
     */
    public ServerRuntimeManager addLast(ServerRuntime runtime) {
        Objects.requireNonNull(runtime, "The runtime must not be null");
        this.runtimes.addLast(runtime);
        return this;
    }

    /**
     * 按注册顺序依次调用各应用的configure，将应用处理器追加到管道尾部；由
     * {@code ServerChannelInitializer} 在协议层就绪后调用（每条新连接一次），多个worker线程
     * 可能并发进入，故应用列表须在服务器启动前确定
     *
     * @param pipeline 新连接的处理器链，不能为null
     */
    public void configure(ChannelPipeline pipeline) {
        Objects.requireNonNull(pipeline, "pipeline can not be null");
        for (ServerRuntime runtime : runtimes) {
            runtime.configure(pipeline);
        }
    }

    /**
     * 关闭全部已注册的应用：按注册顺序依次调用各应用的close，由各应用释放装配期
     * 登记进资源管理器的资源
     * （http应用按登记顺序销毁中间件链、关闭磁盘IO线程池，websocket应用释放消息处理线程池）。
     * <p>
     * 由 {@code BootStrapWebServer#close()} 在传输层线程组关闭之后发起；单个应用关闭抛出的异常
     * 只记录日志，不阻断其余应用的关闭。应用自身的close是幂等的，因此本方法可安全地重复调用
     */
    @Override
    public void close() {
        for (ServerRuntime runtime : runtimes) {
            try {
                runtime.close();
            } catch (Exception e) {
                log.error("Error closing runtime: {}", runtime.getClass().getName(), e);
            }
        }
    }
}
