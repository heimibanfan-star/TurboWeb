package top.turboweb.http.orchestrator;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import top.turboweb.commons.exception.TurboReactiveException;
import top.turboweb.commons.utils.thread.ThreadAssert;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.ReactorResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Reactor 的响应式 HTTP 输出写出器。
 * <p>
 * 该写出器负责将 {@link ReactorResponse} 中的 {@link Flux<ByteBuf>} 响应体
 * 以分块传输（Chunked Transfer-Encoding）的形式异步推送至客户端。
 * </p>
 *
 * <p><b>线程模型：</b></p>
 * 仅允许在虚拟线程（Virtual Thread）中执行，通过
 * {@link ThreadAssert#assertIsVirtualThread()} 进行约束。
 *
 * <p><b>限流等待：</b></p>
 * 构造参数 {@code enableLimit} 开启时，写出前以计数器卡住当前虚拟线程，
 * 直至整个响应流的写出凭据到达终态或等待预算（60 秒）耗尽。
 *
 * @see ReactorResponse
 */
public class ReactorResponseWriter extends AbstractResponseWriter {

    private final boolean enableLimit;

    private static final long MAX_WAIT_SECONDS = 60;

    public ReactorResponseWriter(boolean enableLimit) {
        this.enableLimit = enableLimit;
    }

    /**
     * 执行 Reactor 响应处理逻辑。
     * <p>
     * 先写出响应头（chunked 传输编码 + keep-alive），头写成功后订阅响应体流逐块推送；
     * 头写失败则置败并关闭连接。开启限流时在当前虚拟线程等待写出终态。
     *
     * @param response 响应对象，必须为 {@link ReactorResponse}
     * @param session  当前连接会话，封装了 Netty 的通道信息
     * @return 异步发送结果（代表整个响应流完成状态）
     * @throws IllegalArgumentException 当响应类型不是 {@link ReactorResponse} 时抛出
     */
    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalConnectSession session) {
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
     * 设置分块传输与长连接头后写出响应对象。
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
     * 每个 {@link ByteBuf} 元素包装为 {@link DefaultHttpContent} 后由订阅者推送，
     * 订阅者按写出结果控制请求节奏，形成背压。
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
     * 按写出完成驱动请求节奏（每次请求一个分块），实现流式背压：
     * <ul>
     *   <li>{@link #hookOnSubscribe(Subscription)}：请求第一个分块。</li>
     *   <li>{@link #hookOnNext(HttpContent)}：写出分块，失败则取消订阅，成功后请求下一个。</li>
     *   <li>{@link #hookOnCancel()}：流被取消时置败并关闭连接。</li>
     *   <li>{@link #hookOnError(Throwable)}：流异常时置败并关闭连接。</li>
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
