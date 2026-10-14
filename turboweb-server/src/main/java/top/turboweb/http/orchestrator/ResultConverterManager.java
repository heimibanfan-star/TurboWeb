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
import top.turboweb.http.core.response.AbstractFileResponse;
import top.turboweb.http.core.response.HttpFileResult;
import top.turboweb.http.core.response.HttpInfoResponse;
import top.turboweb.http.core.response.HttpResult;
import top.turboweb.http.core.response.ReactorResponse;
import top.turboweb.serializer.JsonSerializer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 结果转化器管理器：按固定次序组织结果转化链，将业务返回值转化为待写出的 HTTP 响应。
 * <p>
 * 链上每个环节只认领自己支持的返回值类型，认领顺序即类型分派次序：
 * HttpResult（委托自身 createResponse）、String（text/html）、HttpFileResult（文件响应）、
 * HttpResponse（原样认领）、null（空 text/html）、Publisher（转 ReactorResponse 流式响应）、
 * 链尾 JSON 兜底（application/json，恒认领）。
 * </p>
 * <p>
 * 转化入口 {@link #convert(Object, Charset)} 携带装配期判定的缺省字符集
 * （响应编码不受请求头声明影响，恒取缺省配置），入参 result 允许为 null：
 * 环节自建响应的正文编码与 Content-Type 声明一律取该字符集；链产出响应后统一善后——
 * 文本类型（text 前缀与 application/json）的 Content-Type 已声明 charset 时不覆盖、
 * 未声明时以该字符集补写，二进制类型不动；构造期未指定编码的文件响应在此以其
 * URL 编码文件名；{@link HttpInfoResponse} 暂存的原文正文在此编码落体。
 * 序列化失败按环节各自契约传导：兜底环节在调用点原样上抛，
 * 流内元素转为流错误信号，管理器不吞异常。
 * </p>
 */
public class ResultConverterManager {

    /** 处理HttpResult的类型：委托其自身createResponse生成响应。 */
    private static ResultConverter httpResultConverter(JsonSerializer jsonSerializer) {
        return (result, charset) -> result instanceof HttpResult<?> httpResult
                ? ResultConverter.Result.finished(httpResult.createResponse(jsonSerializer, charset))
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果是字符串，按照text/html构建。 */
    private static ResultConverter stringConverter() {
        return (result, charset) -> result instanceof String string
                ? ResultConverter.Result.finished(buildResponse(string, "text/html;charset=" + charset.name(), charset))
                : ResultConverter.Result.shouldContinue();
    }

    /** 处理HttpFileResult的类型：按文件响应生成。 */
    private static ResultConverter fileResultConverter() {
        return (result, charset) -> result instanceof HttpFileResult httpFileResult
                ? ResultConverter.Result.finished(httpFileResult.createResponse(charset))
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果是正常的反应类型，则直接返回。 */
    private static ResultConverter httpResponseConverter() {
        return (result, charset) -> result instanceof HttpResponse response
                ? ResultConverter.Result.finished(response)
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果无返回值，则返回空字符串。 */
    private static ResultConverter nullConverter() {
        return (result, charset) -> result == null
                ? ResultConverter.Result.finished(buildResponse("", "text/html;charset=" + charset.name(), charset))
                : ResultConverter.Result.shouldContinue();
    }

    /** 如果是异步类型，则返回reactor响应对象。 */
    private static ResultConverter publisherConverter(JsonSerializer jsonSerializer) {
        return (result, charset) -> result instanceof Publisher<?> publisher
                ? ResultConverter.Result.finished(new ReactorResponse(processPublisher(publisher, jsonSerializer, charset)))
                : ResultConverter.Result.shouldContinue();
    }

    /** 按照application/json构建：链尾兜底，恒认领。 */
    private static ResultConverter jsonConverter(JsonSerializer jsonSerializer) {
        return (result, charset) -> ResultConverter.Result.finished(buildResponse(
                jsonSerializer.beanToJson(result),
                "application/json;charset=" + charset.name(), charset));
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
     * 将业务返回值经转化链转化为 HTTP 响应，并对产出响应做字符集善后
     * （文本类型 Content-Type 缺 charset 时补写、未编码文件名以其 URL 编码、暂存原文正文以其落体）。
     *
     * @param result  业务返回值，可以为null
     * @param charset 装配期判定的缺省字符集，不能为null
     * @return 转化出的响应，永不为null
     * @throws NullPointerException charset 为 null
     * @throws IllegalStateException 防御性异常：转化链未产出响应（末位兜底环节恒认领，正常不会走到）
     */
    public HttpResponse convert(Object result, Charset charset) {
        Objects.requireNonNull(charset, "The charset must not be null");
        for (ResultConverter converter : converters) {
            ResultConverter.Result converted = converter.convert(result, charset);
            if (converted.status() == ResultConverter.Status.FINISHED) {
                HttpResponse response = converted.httpResponse();
                applyCharset(response, charset);
                return response;
            }
        }
        throw new IllegalStateException("The result converter chain produced no response; the final fallback converter should always claim it");
    }

    /**
     * 对转化产出的响应做字符集善后：文件响应补写待编码文件名、HttpInfoResponse 暂存原文落体、
     * 文本类型 Content-Type 缺 charset 时补写（已声明不覆盖，二进制类型不动）。
     *
     * @param response 转化产出的响应
     * @param charset  装配期判定的缺省字符集
     */
    private static void applyCharset(HttpResponse response, Charset charset) {
        if (response instanceof AbstractFileResponse fileResponse) {
            fileResponse.finalizeFilenameEncoding(charset);
        }
        if (response instanceof HttpInfoResponse infoResponse) {
            infoResponse.materialize(charset);
        }
        stampContentTypeCharset(response, charset);
    }

    /**
     * Content-Type 为文本类型（text 前缀或 application/json）且未声明非空 charset 时，
     * 以给定字符集补写 charset 段；头缺失、非文本类型或已声明 charset 的原样保留。
     *
     * @param response 响应对象
     * @param charset  补写用字符集
     */
    private static void stampContentTypeCharset(HttpResponse response, Charset charset) {
        String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String mimeType = contentType.split(";")[0].trim();
        boolean textType = mimeType.regionMatches(true, 0, "text/", 0, 5)
                || "application/json".equalsIgnoreCase(mimeType);
        if (!textType) {
            return;
        }
        for (String part : contentType.split(";")) {
            String parameter = part.trim().toLowerCase(Locale.ROOT);
            if (parameter.startsWith("charset=") && !parameter.substring("charset=".length()).isEmpty()) {
                return;
            }
        }
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType + ";charset=" + charset.name());
    }

    /**
     * 处理异步类型
     *
     * @param publisher      异步类型
     * @param jsonSerializer json序列化器，流内其他类型元素经其编码为json
     * @param charset        装配期判定的缺省字符集，流内字符串与数字元素按其编码
     * @return Flux<ByteBuf> 响应流
     */
    private static Flux<ByteBuf> processPublisher(Publisher<?> publisher, JsonSerializer jsonSerializer, Charset charset) {
        return Flux.from(publisher)
                .flatMap(val -> {
                    // 处理类型转化
                    if (val instanceof String s) {
                        ByteBuf buf = Unpooled.wrappedBuffer(s.getBytes(charset));
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
                        ByteBuf buf = Unpooled.wrappedBuffer(number.toString().getBytes(charset));
                        return Mono.just(buf);
                    } else {
                        try {
                            String json = jsonSerializer.beanToJson(val);
                            ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(charset));
                            return Mono.just(buf);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    }
                });
    }

    /**
     * 构建一个HttpResponse，正文按给定字符集编码，Content-Type 由调用点声明完整。
     *
     * @param content     内容
     * @param contentType 内容类型
     * @param charset     正文编码字符集
     * @return HttpResponse 响应对象
     */
    private static HttpResponse buildResponse(String content, String contentType, Charset charset) {
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        fullHttpResponse.content().writeBytes(content.getBytes(charset));
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
        fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, fullHttpResponse.content().readableBytes());
        return fullHttpResponse;
    }
}
