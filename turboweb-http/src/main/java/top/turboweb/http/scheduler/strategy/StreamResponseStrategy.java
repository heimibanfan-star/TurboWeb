package top.turboweb.http.scheduler.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.utils.base.BeanUtils;
import top.turboweb.commons.utils.thread.ThreadAssert;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.StreamResponse;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;

/**
 * 处理流式响应的策略
 */
public class StreamResponseStrategy extends ResponseStrategy {
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        ThreadAssert.assertIsVirtualThread();
        if (response instanceof StreamResponse<?> streamResponse) {
            // 重新设置一次响应头
            streamResponse.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            streamResponse.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            ChannelPromise promise = session.getChannel().newPromise();
            try {
                session.getChannel().writeAndFlush(streamResponse).get();
                Charset charset = streamResponse.getContentType().getCharset();
                handleWriteStream(streamResponse.getFlux(), session.getChannel(), promise, charset);
            } catch (InterruptedException | ExecutionException e) {
                promise.setFailure(e);
            }
            return promise;
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }

    /**
     * 处理流式响应
     *
     * @param flux       流式数据
     * @param channel    通道
     * @param promise    通道的promise
     * @param charset    字符集
     */
    private void handleWriteStream(Flux<?> flux, Channel channel, ChannelPromise promise, Charset charset) {
        flux.flatMap(val -> {
                    HttpContent content;
                    // 如果是字符串类型直接返回
                    if (val instanceof String s) {
                        content = new DefaultHttpContent(Unpooled.wrappedBuffer(s.getBytes(charset)));
                    } else if (val instanceof byte[] bytes) {
                        content = new DefaultHttpContent(Unpooled.wrappedBuffer(bytes));
                    } else if (val instanceof ByteBuffer byteBuffer) {
                        content = new DefaultHttpContent(Unpooled.wrappedBuffer(byteBuffer));
                    } else {
                        try {
                            String json = BeanUtils.getObjectMapper().writeValueAsString(val);
                            content = new DefaultHttpContent(Unpooled.wrappedBuffer(json.getBytes(charset)));
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    }
                    return Mono.just(content);
                }).doFinally(onFinally -> {
                    channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                })
                .subscribe(
                        channel::writeAndFlush,
                        promise::setFailure,
                        promise::setSuccess
                );
    }
}
