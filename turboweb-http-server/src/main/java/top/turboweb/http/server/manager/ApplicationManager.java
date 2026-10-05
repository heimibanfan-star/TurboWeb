package top.turboweb.http.server.manager;

import io.netty.channel.ChannelPipeline;
import top.turboweb.http.server.application.Application;

import java.util.LinkedList;
import java.util.Objects;

/**
 * 应用管理器：应用级处理器在连接管道上的装配入口。
 * <p>
 * 本类只管应用——持有 {@link #addFirst(Application)} / {@link #addLast(Application)} 注册的应用列表，
 * 并在 {@link #configure(ChannelPipeline)} 时按注册顺序把各应用追加到管道尾部。连接限流、ssl、
 * http编解码与http2等服务器级传输处理器的装配不属于本类，由
 * {@code top.turboweb.http.server.channel.ServerChannelInitializer} 在应用之前完成，
 * 因此本类不依赖 {@link top.turboweb.http.server.config.ServerConfig}。
 * <p>
 * 应用的注册须在服务器启动前确定：{@code configure} 会被多个worker线程并发调用，
 * 启动后再修改应用列表存在并发风险。
 *
 * <pre>{@code
 * ApplicationManager manager = new ApplicationManager()
 *         .addLast(httpRequestApplication)
 *         .addLast(webSocketApplication);
 * // 每条新连接协议层就绪后由 ServerChannelInitializer 调用：manager.configure(pipeline);
 * }</pre>
 */
public class ApplicationManager {

    /**
     * 已注册的应用列表，装配顺序即列表顺序（addFirst插队头、addLast排队尾）
     */
    private final LinkedList<Application> applications = new LinkedList<>();

    /**
     * 将应用插到应用列表头部（装配时最先执行），应用为null立即抛出 {@link NullPointerException}
     */
    public ApplicationManager addFirst(Application application) {
        Objects.requireNonNull(application, "应用不能为null");
        this.applications.addFirst(application);
        return this;
    }

    /**
     * 将应用追加到应用列表尾部（装配时最后执行），应用为null立即抛出 {@link NullPointerException}
     */
    public ApplicationManager addLast(Application application) {
        Objects.requireNonNull(application, "应用不能为null");
        this.applications.addLast(application);
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
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline can not be null");
        }
        for (Application application : applications) {
            application.configure(pipeline);
        }
    }
}