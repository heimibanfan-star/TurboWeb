package top.turboweb.http.scheduler.strategy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import top.turboweb.commons.exception.TurboReactiveException;
import top.turboweb.commons.utils.thread.ThreadAssert;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.ReactorResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@code ReactorResponseStrategy}
 * <p>
 * 基于 Reactor 的响应式 HTTP 输出策略。
 * 该策略负责将 {@link ReactorResponse} 中的 {@link Flux<ByteBuf>} 响应体
 * 以分块传输（Chunked Transfer-Encoding）的形式异步推送至客户端。
 * </p>
 *
 * <h2>特性与设计目标</h2>
 * <ul>
 *   <li>支持响应式流（Flux → HTTP Chunked）自动分块传输。</li>
 *   <li>与虚拟线程兼容，允许同步等待响应完成。</li>
 *   <li>自动设置 <code>Transfer-Encoding: chunked</code> 与 <code>Connection: keep-alive</code>。</li>
 *   <li>背压控制：基于 {@link BaseSubscriber} 每次请求 1 个数据块。</li>
 *   <li>错误、取消、完成事件全覆盖，防止资源泄漏。</li>
 *   <li>可选限流阻塞，用于确保虚拟线程等待响应完成。</li>
 * </ul>
 *
 * <h2>使用说明</h2>
 * 框架在检测到响应类型为 {@link ReactorResponse} 时会自动启用本策略。
 * 它会先发送响应头，然后异步订阅 Reactor 流，并通过
 * {@link ReactorWriter} 将每个数据块写入 Netty Channel。
 *
 * <p>
 * 若启用限流（{@code enableLimit = true}），当前虚拟线程将阻塞等待
 * {@link ChannelPromise} 完成，最长 60 秒。
 * </p>
 *
 * @see ReactorResponse
 * @see ResponseStrategy
 */
public class ReactorResponseStrategy extends ResponseStrategy {

    private final boolean enableLimit;

    private static final long MAX_WAIT_SECONDS = 60;

    public ReactorResponseStrategy(boolean enableLimit) {
        this.enableLimit = enableLimit;
    }

    /**
     * 执行 Reactor 响应处理逻辑。
     * <p>
     * 该方法仅支持 {@link ReactorResponse} 类型的响应。
     * 在写入响应头后，会订阅内部的 {@link Flux<ByteBuf>} 流，并逐块写入客户端。
     * </p>
     *
     * @param response HTTP 响应对象（必须为 {@link ReactorResponse} 类型）
     * @param session  内部连接会话
     * @return 异步发送结果（代表整个响应流完成状态）
     * @throws IllegalArgumentException 当响应类型不是 {@link ReactorResponse} 时抛出
     */
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        ThreadAssert.assertIsVirtualThread();
        if (response instanceof ReactorResponse reactorResponse) {
            ChannelPromise promise = session.getChannel().newPromise();
            writeHeader(response, session)
                    .addListener(f -> {
                        if (f.isSuccess()) {
                            writeBody(reactorResponse.getFlux(), session, promise);
                        } else {
                            promise.setFailure(f.cause());
                            session.close();
                        }
                    });
            // 判断是否开启限流
            if (enableLimit) {
                CountDownLatch latch = new CountDownLatch(1);
                promise.addListener(f -> latch.countDown());
                // 卡住虚拟线程
                try {
                    boolean ignore = latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException ignore) {
                }
            }
            return promise;
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }

    /**
     * 写入 HTTP 响应头。
     * <p>
     * 会自动添加：
     * <ul>
     *   <li>{@code Transfer-Encoding: chunked}</li>
     *   <li>{@code Connection: keep-alive}</li>
     * </ul>
     * </p>
     *
     * @param response 响应对象
     * @param session  内部连接会话
     * @return 表示写入结果的异步操作
     */
    private ChannelFuture writeHeader(HttpResponse response, InternalConnectSession session) {
        // 设置特定的响应头
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        return session.getChannel().writeAndFlush(response);
    }

    /**
     * 通过 Reactor {@link Flux} 将响应体流式写入客户端。
     * <p>
     * 每个 {@link ByteBuf} 对象将被包装为 {@link DefaultHttpContent} 并写入管道，
     * 当流结束时发送 {@link LastHttpContent#EMPTY_LAST_CONTENT} 表示结束。
     * </p>
     *
     * @param flux    响应体数据流
     * @param session 内部连接会话
     * @param promise 异步回调对象
     */
    private void writeBody(Flux<ByteBuf> flux, InternalConnectSession session, ChannelPromise promise) {
        flux.map(DefaultHttpContent::new).subscribe(new ReactorWriter(session, promise));
    }

    /**
     * 内部类：基于 Reactor 的订阅写入器。
     * <p>
     * 负责将上游 {@link HttpContent} 流逐块写入 Netty Channel，
     * 并通过显式 {@code request(1)} 实现背压控制。
     * </p>
     *
     * <h3>生命周期事件：</h3>
     * <ul>
     *   <li>{@link #hookOnSubscribe(Subscription)}：初始订阅，请求第一个分块。</li>
     *   <li>{@link #hookOnNext(HttpContent)}：收到一个分块，写入管道并在成功后请求下一个。</li>
     *   <li>{@link #hookOnError(Throwable)}：流中出错，设置失败并关闭连接。</li>
     *   <li>{@link #hookOnCancel()}：上游取消订阅（客户端断开或写失败）。</li>
     *   <li>{@link #hookOnComplete()}：流正常结束，发送 {@link LastHttpContent#EMPTY_LAST_CONTENT}。</li>
     * </ul>
     */
    private static class ReactorWriter extends BaseSubscriber<HttpContent> {

        private final static long SUBSCRIBE_NUM = 1;

        private final InternalConnectSession connectSession;
        private final ChannelPromise promise;

        private ReactorWriter(InternalConnectSession connectSession, ChannelPromise promise) {
            this.connectSession = connectSession;
            this.promise = promise;
        }

        /**
         * 初始化订阅时触发。
         * <p>请求第一个数据块，建立最小背压窗口。</p>
         */
        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            request(SUBSCRIBE_NUM);
        }

        /**
         * 每当接收到一个 {@link HttpContent} 分块时触发。
         * <p>
         * 将内容写入 Channel，监听写入结果：
         * <ul>
         *   <li>若写入失败，则调用 {@link #cancel()} 终止上游流。</li>
         *   <li>若写入成功，则请求下一个分块。</li>
         * </ul>
         * </p>
         */
        @Override
        protected void hookOnNext(HttpContent content) {
            // 尝试将ByteBuf写入管道
            this.connectSession.getChannel().writeAndFlush(content)
                    .addListener(future -> {
                       // 判断当前写入是否成功
                       if (!future.isSuccess()) {
                           // 取消对流的订阅
                           cancel();
                       }
                       // 请求下一个分块
                        request(SUBSCRIBE_NUM);
                    });
        }

        /**
         * 当流被主动取消时触发。
         * <p>
         * 通常是由于写入错误或客户端中断。
         * 会设置 Promise 为失败状态，并关闭连接。
         * </p>
         */
        @Override
        protected void hookOnCancel() {
            // 当流被取消时触发设置promise回调
            promise.setFailure(new TurboReactiveException("Stream cancelled"));
            // 关闭当前连接的channel
            connectSession.close();
        }

        /**
         * 当上游流正常完成时触发。
         * <p>
         * 向客户端写入 {@link LastHttpContent#EMPTY_LAST_CONTENT}
         * 表示 HTTP 响应流的结束，并完成 Promise。
         * </p>
         */
        @Override
        protected void hookOnComplete() {
            // 当流被正常完成时触发, 写入最后结束分块
            connectSession.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise);
        }

        /**
         * 当上游流出现异常时触发。
         * <p>
         * 会立即设置 Promise 失败并关闭连接。
         * </p>
         */
        @Override
        protected void hookOnError(Throwable throwable) {
            // 当流发生错误时触发
            promise.setFailure(throwable);
            // 关闭当前连接的channel
            connectSession.close();
        }
    }
}
