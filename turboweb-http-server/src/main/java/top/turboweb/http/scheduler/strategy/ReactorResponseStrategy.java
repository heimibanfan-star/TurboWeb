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
 * 处理reactor响应的策略
 */
public class ReactorResponseStrategy extends ResponseStrategy {

    private final boolean enableLimit;

    public ReactorResponseStrategy(boolean enableLimit) {
        this.enableLimit = enableLimit;
    }

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
     * 将响应头发送到客户端
     *
     * @param response 响应对象
     * @param session  连接会话
     * @return 异步对象
     */
    private ChannelFuture writeHeader(HttpResponse response, InternalConnectSession session) {
        // 设置特定的响应头
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        return session.getChannel().writeAndFlush(response);
    }

    /**
     * 将响应体发送到客户端
     *
     * @param flux    响应体
     * @param session 连接会话
     * @param promise 异步对象
     * @param charset 字符集
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
