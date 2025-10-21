package top.turboweb.http.processor;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.ConnectSession;

/**
 * TurboWeb 内部核心处理器抽象类。
 * <p>
 * 该处理器用于系统内部 HTTP 请求的处理链，处理器链中的每个处理器负责完成特定的请求逻辑（如：跨域、异常处理、中间件调用）。
 * </p>
 * <p>
 * <strong>注意：</strong>此类为系统内部使用，不允许用户扩展或直接修改。
 * </p>
 * <p>
 * 处理器以「链式调用」的方式组织，通过 {@link #setNextProcessor(Processor)} 构建处理链。
 * 每个处理器完成自身逻辑后，可选择调用 {@link #next(FullHttpRequest, ConnectSession)} 将请求交给下一个处理器，
 * 若处理器能够直接生成响应，则可直接返回 {@link HttpResponse}。
 * </p>
 */
public abstract class Processor {

    private Processor nextProcessor;

    /**
     * 执行当前处理器的核心逻辑。
     * <p>
     * 子类必须实现此方法以处理 HTTP 请求。方法可以在处理完成自身逻辑后调用 {@link #next(FullHttpRequest, ConnectSession)}
     * 将请求交给下一个处理器，或者直接返回一个 {@link HttpResponse} 对象。
     * </p>
     *
     * @param fullHttpRequest 完整的 HTTP 请求对象
     * @param connectSession 当前请求关联的连接会话
     * @return HTTP 响应对象，可为 {@code null} 表示未生成响应
     */
    public abstract HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession);

    /**
     * 调用处理链中的下一个处理器。
     * <p>
     * 子类在 {@link #invoke(FullHttpRequest, ConnectSession)} 中可调用此方法继续执行链上的后续处理逻辑。
     * 若当前处理器已是链的末尾，则返回 {@code null}。
     * </p>
     *
     * @param fullHttpRequest 完整的 HTTP 请求对象
     * @param connectSession 当前请求关联的连接会话
     * @return 下一个处理器返回的 HTTP 响应结果，若无下一个处理器则返回 {@code null}
     */
    protected HttpResponse next(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
        if (nextProcessor != null) {
            return nextProcessor.invoke(fullHttpRequest, connectSession);
        }
        return null;
    }

    /**
     * 设置处理链中的下一个处理器。
     * <p>
     * 可通过此方法构建多级处理链，实现请求的顺序传递和处理。
     * </p>
     *
     * @param nextProcessor 下一个处理器实例
     */
    public void setNextProcessor(Processor nextProcessor) {
        this.nextProcessor = nextProcessor;
    }
}
