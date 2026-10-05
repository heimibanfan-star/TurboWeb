package top.turboweb.http.testkit;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

import java.util.List;

/**
 * 测试用记录型中间件：把 init / invoke / destroy 三个生命周期事件按名字写进调用方提供的列表。
 * <p>
 * 用途有三：装配断言（链顺序由 init 顺序证明）、销毁断言（应用 close 后 destroy 必须出现）、
 * 应答断言（{@code payload} 作为 invoke 返回值经响应转换器变成 text/html 响应体）。
 * <p>
 * 注意：{@code events} 由调用方共享，若在多线程场景（真实服务器在 worker/虚拟线程里调用 invoke）
 * 使用，请传入 {@code CopyOnWriteArrayList}。
 */
public class RecordingMiddleware extends Middleware {

    private final String name;
    private final List<String> events;
    private final String payload;

    public RecordingMiddleware(String name, List<String> events) {
        this(name, events, null);
    }

    public RecordingMiddleware(String name, List<String> events, String payload) {
        this.name = name;
        this.events = events;
        this.payload = payload;
    }

    public String name() {
        return name;
    }

    @Override
    public void init(Middleware chain) {
        events.add("init:" + name);
    }

    @Override
    public Object invoke(HttpContext ctx) {
        events.add("invoke:" + name);
        if (payload != null) {
            return payload;
        }
        return next(ctx);
    }

    @Override
    public void destroy() {
        events.add("destroy:" + name);
    }
}
