package top.turboweb.http.scheduler.strategy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import reactor.core.publisher.Flux;

import top.turboweb.commons.utils.thread.ThreadAssert;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.ReactorResponse;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Reactor 响应策略。
 * <p>
 * 该策略用于处理 {@link ReactorResponse} 类型的响应，支持基于 Reactor {@link Flux} 的响应流，
 * 通过分块传输（Chunked Transfer-Encoding）方式向客户端推送数据。
 * </p>
 *
 * <p>
 * 该策略通常用于响应式流场景，与虚拟线程兼容。
 * 框架会确保该逻辑仅在虚拟线程中执行，以避免阻塞 I/O 线程。
 * </p>
 *
 * <p>
 * 当启用限流（{@code enableLimit = true}）时，将通过 {@link CountDownLatch}
 * 限制虚拟线程在 60 秒内阻塞等待响应完成，以防止 Reactor 流绕过虚拟线程限流。
 * </p>
 *
 * <h3>主要特性：</h3>
 * <ul>
 *   <li>支持响应式数据流分块输出（Flux → HTTP chunked）。</li>
 *   <li>自动设置 <code>Transfer-Encoding: chunked</code> 与 <code>Connection: keep-alive</code>。</li>
 *   <li>可选限流保护，防止虚拟线程提前退出。</li>
 *   <li>流式发送失败时自动关闭连接。</li>
 * </ul>
 *
 * @see ReactorResponse
 * @see ResponseStrategy
 */
public class ReactorResponseStrategy extends ResponseStrategy {

    private final boolean enableLimit;

    public ReactorResponseStrategy(boolean enableLimit) {
        this.enableLimit = enableLimit;
    }

    /**
     * 执行 Reactor 响应处理逻辑。
     * <p>
     * 该方法仅支持 {@link ReactorResponse} 类型的响应，
     * 在写入响应头后异步推送 Flux 数据流。
     * </p>
     *
     * @param response HTTP 响应对象（必须为 {@link ReactorResponse} 类型）
     * @param session  内部连接会话
     * @return 异步发送结果
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
                            writeBody(reactorResponse.getFlux(), session, promise, reactorResponse.getCharset());
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
                    boolean ignore = latch.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException ignore) {
                }
            }
            return promise;
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }

    /**
     * 将响应头写入客户端。
     * <p>
     * 会自动添加：
     * <ul>
     *   <li><b>Transfer-Encoding:</b> chunked</li>
     *   <li><b>Connection:</b> keep-alive</li>
     * </ul>
     * </p>
     *
     * @param response 响应对象
     * @param session  内部连接会话
     * @return 写入操作的异步结果
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
     * @param charset 字符集（暂未使用，用于扩展文本流场景）
     */
    private void writeBody(Flux<ByteBuf> flux, InternalConnectSession session, ChannelPromise promise, Charset charset) {
        flux.map(DefaultHttpContent::new)
                .subscribe(
                        val -> session.getChannel().writeAndFlush(val),
                        err -> {
                            promise.setFailure(err);
                            session.close();
                        },
                        () -> session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise)
                );
    }
}
