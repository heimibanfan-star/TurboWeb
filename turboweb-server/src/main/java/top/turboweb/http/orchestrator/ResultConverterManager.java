package top.turboweb.http.orchestrator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.core.response.HttpFileResult;
import top.turboweb.http.core.response.HttpResult;
import top.turboweb.http.core.response.ReactorResponse;
import top.turboweb.serializer.JsonSerializer;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * 结果转化器管理器：按固定次序组织结果转化链，将业务返回值转化为待写出的 HTTP 响应。
 * <p>
 * 链上每个环节只认领自己支持的返回值类型，认领顺序即类型分派次序：
 * HttpResult（委托自身 createResponse）、String（text/html）、HttpFileResult（文件响应）、
 * HttpResponse（原样认领）、null（空 text/html）、Publisher（转 ReactorResponse 流式响应）、
 * 链尾 JSON 兜底（application/json，恒认领）。
 * </p>
 * <p>
 * 转化入口 {@link #convert(Object)} 入参允许为 null；序列化失败按环节各自契约传导：
 * 兜底环节在调用点原样上抛，流内元素转为流错误信号，管理器不吞异常。
 * </p>
 */
public class ResultConverterManager {

    /** 处理HttpResult的类型：委托其自身createResponse生成响应。 */
    private static ResultConverter httpResultConverter(JsonSerializer jsonSerializer) {
        return result -> result instanceof HttpResult<?> httpResult
                ? ResultConverter.Result.finished(httpResult.createResponse(jsonSerializer))
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果是字符串，按照text/html构建。 */
    private static ResultConverter stringConverter() {
        return result -> result instanceof String string
                ? ResultConverter.Result.finished(buildResponse(string, "text/html;charset=" + GlobalConfig.getResponseCharset().name()))
                : ResultConverter.Result.shouldContinue();
    }

    /** 处理HttpFileResult的类型：按文件响应生成。 */
    private static ResultConverter fileResultConverter() {
        return result -> result instanceof HttpFileResult httpFileResult
                ? ResultConverter.Result.finished(httpFileResult.createResponse())
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果是正常的反应类型，则直接返回。 */
    private static ResultConverter httpResponseConverter() {
        return result -> result instanceof HttpResponse response
                ? ResultConverter.Result.finished(response)
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果无返回值，则返回空字符串。 */
    private static ResultConverter nullConverter() {
        return result -> result == null
                ? ResultConverter.Result.finished(buildResponse("", "text/html;charset=" + GlobalConfig.getResponseCharset().name()))
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果是异步类型，则返回reactor响应对象。 */
    private static ResultConverter publisherConverter(JsonSerializer jsonSerializer) {
        return result -> result instanceof Publisher<?> publisher
                ? ResultConverter.Result.finished(new ReactorResponse(processPublisher(publisher, jsonSerializer)))
                : ResultConverter.Result.shouldContinue();
    }

    /** 按照application/json构建：链尾兜底，恒认领。 */
    private static ResultConverter jsonConverter(JsonSerializer jsonSerializer) {
        return result -> ResultConverter.Result.finished(buildResponse(
                jsonSerializer.beanToJson(result),
                "application/json;charset=" + GlobalConfig.getResponseCharset().name()));
    }

    /**
     * 结果转化链：构造期一次建定、不可变；环节按声明次序逐个认领，末位兜底环节恒完结。
     */
    private final List<ResultConverter> converters;

    /**
     * 构造结果转化器管理器并建立转化链。
     *
     * @param jsonSerializer json序列化器，供 HttpResult 生成响应、流内对象元素编码与兜底 JSON 转化使用
     */
    public ResultConverterManager(JsonSerializer jsonSerializer) {
        this.converters = List.of(
                httpResultConverter(jsonSerializer),
                stringConverter(),
                fileResultConverter(),
                httpResponseConverter(),
                nullConverter(),
                publisherConverter(jsonSerializer),
                jsonConverter(jsonSerializer));
    }

    /**
     * 将业务返回值经转化链转化为 HTTP 响应。
     *
     * @param result 业务返回值，可以为null
     * @return 转化出的响应，永不为null
     * @throws IllegalStateException 防御性异常：转化链未产出响应（末位兜底环节恒认领，正常不会走到）
     */
    public HttpResponse convert(Object result) {
        for (ResultConverter converter : converters) {
            ResultConverter.Result converted = converter.convert(result);
            if (converted.status() == ResultConverter.Status.FINISHED) {
                return converted.httpResponse();
            }
        }
        throw new IllegalStateException("结果转化链未产出响应, 末位兜底环节应恒认领");
    }

    /**
     * 处理异步类型
     *
     * @param publisher      异步类型
     * @param jsonSerializer json序列化器，流内其他类型元素经其编码为json
     * @return Flux<ByteBuf> 响应流
     */
    private static Flux<ByteBuf> processPublisher(Publisher<?> publisher, JsonSerializer jsonSerializer) {
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
    private static HttpResponse buildResponse(String content, String contentType) {
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.content().writeBytes(content.getBytes(GlobalConfig.getResponseCharset()));
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, fullHttpResponse.content().readableBytes());
        return fullHttpResponse;
    }
}
