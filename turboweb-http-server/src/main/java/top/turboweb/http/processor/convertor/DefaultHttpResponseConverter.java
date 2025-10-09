package top.turboweb.http.processor.convertor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.response.ReactorResponse;

import java.nio.ByteBuffer;

/**
 * 默认的HttpResponse转化器
 */
public class DefaultHttpResponseConverter implements HttpResponseConverter {

    private final JsonSerializer jsonSerializer;

    public DefaultHttpResponseConverter(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    @Override
    public HttpResponse convertor(Object result) {
        return switch (result) {
            // 处理HttpResult的类型
            case HttpResult<?> httpResult -> httpResult.createResponse(jsonSerializer);
            // 如果是字符串，按照text/html构建
            case String string ->
                    buildResponse(string, "text/html;charset=" + GlobalConfig.getResponseCharset().name());
            // 处理HttpFileResult的类型
            case HttpFileResult httpFileResult -> httpFileResult.createResponse();
            // 如果是正常的反应类型，则直接返回
            case HttpResponse httpResponse -> httpResponse;
            // 如果无返回值，则返回空字符串
            case null -> buildResponse("", "text/html;charset=" + GlobalConfig.getResponseCharset().name());
            // 如果是异步类型，则返回reactor响应对象
            case Publisher<?> publisher -> new ReactorResponse(processPublisher(publisher));
            // 按照application/json构建
            default -> {
                String json = jsonSerializer.beanToJson(result);
                yield buildResponse(json, "application/json;charset=" + GlobalConfig.getResponseCharset().name());
            }
        };
    }

    /**
     * 处理异步类型
     *
     * @param publisher 异步类型
     * @return Flux<ByteBuf> 响应流
     */
    private Flux<ByteBuf> processPublisher(Publisher<?> publisher) {
        return Flux.from(publisher)
                .flatMap(val -> {
                    // 处理类型转化
                    if (val instanceof String s) {
                        ByteBuf buf = Unpooled.wrappedBuffer(s.getBytes(GlobalConfig.getResponseCharset()));
                        return Mono.just(buf);
                    } else if (val instanceof byte[] bytes) {
                        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                        return Mono.just(buf);
                    } else if (val instanceof ByteBuffer buffer) {
                        ByteBuf buf = Unpooled.wrappedBuffer(buffer);
                        return Mono.just(buf);
                    } else if (val instanceof ByteBuf byteBuf) {
                        return Mono.just(byteBuf);
                    } else if (val instanceof Number number) {
                        ByteBuf buf = Unpooled.wrappedBuffer(number.toString().getBytes(GlobalConfig.getResponseCharset()));
                        return Mono.just(buf);
                    } else {
                        try {
                            String json = jsonSerializer.beanToJson(val);
                            ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(GlobalConfig.getResponseCharset()));
                            return Mono.just(buf);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    }
                });
    }

    /**
     * 构建一个HttpResponse
     *
     * @param content     内容
     * @param contentType 内容类型
     * @return HttpResponse 响应对象
     */
    private HttpResponse buildResponse(String content, String contentType) {
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.content().writeBytes(content.getBytes(GlobalConfig.getResponseCharset()));
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, fullHttpResponse.content().readableBytes());
        return fullHttpResponse;
    }
}
