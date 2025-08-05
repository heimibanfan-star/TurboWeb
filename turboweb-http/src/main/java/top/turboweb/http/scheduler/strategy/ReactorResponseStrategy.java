package top.turboweb.http.scheduler.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.utils.base.BeanUtils;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.ReactorResponse;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * 处理reactor响应的策略
 */
public class ReactorResponseStrategy extends ResponseStrategy {
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        if (response instanceof ReactorResponse<?> reactorResponse) {
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
    private void writeBody(Flux<?> flux, InternalConnectSession session, ChannelPromise promise, Charset charset) {
        flux.flatMap(val -> {
                    HttpContent content;
                    // 处理类型转化
                    if (val instanceof String s) {
                        ByteBuf buf = Unpooled.wrappedBuffer(s.getBytes(charset));
                        content = new DefaultHttpContent(buf);
                    } else if (val instanceof byte[] bytes) {
                        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                        content = new DefaultHttpContent(buf);
                    } else if (val instanceof ByteBuffer buffer) {
                        ByteBuf buf = Unpooled.wrappedBuffer(buffer);
                        content = new DefaultHttpContent(buf);
                    } else if (val instanceof ByteBuf byteBuf) {
                        content = new DefaultHttpContent(byteBuf);
                    } else if (val instanceof Number number) {
                        ByteBuf buf = Unpooled.wrappedBuffer(number.toString().getBytes(charset));
                        content = new DefaultHttpContent(buf);
                    } else {
                        try {
                            String json = BeanUtils.getObjectMapper().writeValueAsString(val);
                            ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(charset));
                            content = new DefaultHttpContent(buf);
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    }
                    return Mono.just(content);
                })
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
