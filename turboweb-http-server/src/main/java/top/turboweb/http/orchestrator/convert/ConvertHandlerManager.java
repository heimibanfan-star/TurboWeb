package top.turboweb.http.orchestrator.convert;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.response.HttpResult;
import top.turboweb.http.response.StreamResponseMessage;
import top.turboweb.serializer.JsonSerializer;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结果转化处理器管理器：按内置、自定义、JSON 默认三级次序，将业务返回值转化为待写出的响应消息。
 * <p>
 * 内置环节按固定次序认领各自支持的返回值类型：HttpResult（装配为字节消息）、
 * String（text/html）、null（空 text/html）、Publisher（转 StreamResponseMessage 流式消息）；
 * 自定义环节经构造器列表传入，按处理器具体类登记到单独容器，内置环节之后按登记次序认领；
 * 两级都不认领的输入由 JSON 默认环节（application/json，恒认领）兜底，保证任意输入都有确定走向。
 * </p>
 * <p>
 * <b>注册约束：</b>自定义处理器为内置类型（含 JSON 默认环节）时构造期抛出
 * {@link IllegalArgumentException}，同一处理器类出现多次抛出 {@link IllegalStateException}，
 * 列表或元素为 null 抛出 {@link NullPointerException}。
 * </p>
 * <p>
 * 转化入口 {@link #convert(Object, Charset)} 携带装配期判定的缺省字符集
 * （响应编码不受请求头声明影响，恒取缺省配置），入参 result 允许为 null：
 * 环节自建消息的正文编码与 Content-Type 声明一律取该字符集，
 * 内容长度由写出环节按实际响应体补齐。
 * 序列化失败按环节各自契约传导：默认环节在调用点原样上抛，
 * 流内元素转为流错误信号，管理器不吞异常。
 * </p>
 */
public class ConvertHandlerManager {

    /** 内置处理器类集合（含 JSON 默认环节），自定义注册按具体类拒登内置类型。 */
    private static final Set<Class<? extends ConvertHandler>> BUILTIN_CONVERTER_TYPES = Set.of(
            HttpResultConvertHandler.class,
            StringConvertHandler.class,
            NullConvertHandler.class,
            PublisherConvertHandler.class,
            JsonResultConvertHandler.class);

    /**
     * 内置结果转化处理器：构造期一次建定、不可变；环节按声明次序逐个认领，先于自定义环节咨询。
     */
    private final List<ConvertHandler> builtinConverters;
    /**
     * 自定义结果转化处理器：按登记次序排列的不可变列表，内置环节之后逐个咨询。
     */
    private final List<ConvertHandler> customConverters;
    /**
     * JSON 默认环节：恒认领，兜底内置与自定义环节都未认领的输入。
     */
    private final ConvertHandler defaultConverter;

    /**
     * 构造结果转化处理器管理器。
     *
     * @param jsonSerializer  json序列化器，兜底环节与流内对象元素编码经其序列化，不能为null
     * @param customConverters 自定义结果转化处理器列表，不能为null、元素不能为null
     * @throws NullPointerException     json序列化器或自定义列表为null、列表元素为null
     * @throws IllegalArgumentException 自定义处理器为内置类型（含 JSON 默认环节）
     * @throws IllegalStateException    同一处理器类在列表中出现多次
     */
    public ConvertHandlerManager(JsonSerializer jsonSerializer, List<ConvertHandler> customConverters) {
        Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        Objects.requireNonNull(customConverters, "The custom convert handler list must not be null");
        this.builtinConverters = List.of(
                new HttpResultConvertHandler(jsonSerializer),
                new StringConvertHandler(),
                new NullConvertHandler(),
                new PublisherConvertHandler(jsonSerializer));
        this.customConverters = registerCustomConverters(customConverters);
        this.defaultConverter = new JsonResultConvertHandler(jsonSerializer);
    }

    /**
     * 将业务返回值经内置、自定义两级转化链转化为响应消息，两级都不认领时由 JSON 默认转化器认领；
     * 恒产出一条响应消息，永不为null。
     *
     * @param result  业务返回值，可能为null
     * @param charset 装配期判定的缺省字符集，用于正文编码与缺省内容类型声明
     * @return 转化出的响应消息，永不为null
     * @throws NullPointerException charset 为 null
     */
    public HttpResponseMessage<?> convert(Object result, Charset charset) {
        Objects.requireNonNull(charset, "The charset must not be null");
        for (ConvertHandler converter : this.builtinConverters) {
            ConvertHandler.Result converted = converter.convert(result, charset);
            if (converted.status() == ConvertHandler.Status.FINISHED) {
                return converted.message();
            }
        }
        for (ConvertHandler converter : this.customConverters) {
            ConvertHandler.Result converted = converter.convert(result, charset);
            if (converted.status() == ConvertHandler.Status.FINISHED) {
                return converted.message();
            }
        }
        // 执行到默认环节即内置与自定义两级全部放行，默认环节恒认领并收尾
        return this.defaultConverter.convert(result, charset).message();
    }

    /**
     * 把自定义处理器列表按具体类登记为不可变表：元素为null拒登，内置类型拒登，同一类重复登记拒登。
     *
     * @param customConverters 用户自定义的结果转化处理器列表
     * @return 按登记次序排列的不可变处理器列表
     * @throws NullPointerException      列表元素为null
     * @throws IllegalArgumentException  处理器类为内置类型
     * @throws IllegalStateException     同一处理器类出现多次
     */
    private static List<ConvertHandler> registerCustomConverters(List<ConvertHandler> customConverters) {
        Map<Class<? extends ConvertHandler>, ConvertHandler> table = new LinkedHashMap<>();
        for (ConvertHandler converter : customConverters) {
            Objects.requireNonNull(converter, "The convert handler must not be null");
            Class<? extends ConvertHandler> converterType = converter.getClass();
            if (BUILTIN_CONVERTER_TYPES.contains(converterType)) {
                throw new IllegalArgumentException(
                        "Can not register a built-in convert handler: " + converterType.getName());
            }
            if (table.putIfAbsent(converterType, converter) != null) {
                throw new IllegalStateException("Duplicate convert handler registration: " + converterType.getName());
            }
        }
        return List.copyOf(table.values());
    }

    /** 内置环节：认领 HttpResult，装配为字节响应消息。 */
    public static final class HttpResultConvertHandler implements ConvertHandler {

        /** Content-Type 头名 */
        private static final String CONTENT_TYPE = "Content-Type";
        /** Content-Length 头名 */
        private static final String CONTENT_LENGTH = "Content-Length";

        private final JsonSerializer jsonSerializer;

        /**
         * 构造 HttpResult 转化处理器。
         *
         * @param jsonSerializer json序列化器，非字符串数据经其序列化为 JSON，不能为null
         */
        public HttpResultConvertHandler(JsonSerializer jsonSerializer) {
            this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        }

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return result instanceof HttpResult httpResult
                    ? ConvertHandler.Result.finished(toByteMessage(httpResult, charset))
                    : ConvertHandler.Result.shouldContinue();
        }

        /**
         * 把 HttpResult 值对象装配为字节响应消息：数据为 String 时按 text/html 输出，
         * 其余数据经 json 序列化器按 application/json 输出，正文与 Content-Type 的 charset 段
         * 均取给定字符集；调用方头先整体写入，内容类型与内容长度以框架按实际响应体算出的值为准。
         *
         * @param httpResult 响应值对象
         * @param charset    装配期判定的缺省字符集
         * @return 字节响应消息
         */
        private ByteResponseMessage toByteMessage(HttpResult httpResult, Charset charset) {
            String content;
            String contentType;
            if (httpResult.data() instanceof String stringData) {
                content = stringData;
                contentType = "text/html;charset=" + charset.name();
            } else {
                content = jsonSerializer.beanToJson(httpResult.data());
                contentType = "application/json;charset=" + charset.name();
            }
            byte[] body = content == null ? new byte[0] : content.getBytes(charset);
            ByteResponseMessage message = new ByteResponseMessage(httpResult.statusLine(), body);
            for (Map.Entry<String, List<String>> entry : httpResult.headers().asMap().entrySet()) {
                for (String value : entry.getValue()) {
                    message.headers().add(entry.getKey(), value);
                }
            }
            message.headers().set(CONTENT_TYPE, contentType);
            message.headers().set(CONTENT_LENGTH, String.valueOf(body.length));
            return message;
        }
    }

    /** 内置环节：认领 String，按照 text/html 装配字节消息。 */
    public static final class StringConvertHandler implements ConvertHandler {

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return result instanceof String string
                    ? ConvertHandler.Result.finished(buildByteResponse(string, "text/html;charset=" + charset.name(), charset))
                    : ConvertHandler.Result.shouldContinue();
        }
    }

    // ---------- 旧响应体系环节：随响应体系消息化迁移停用，待新线补齐对应能力后处置 ----------

    ///** 内置环节：认领 HttpFileResult，按文件响应生成。 */
    //public static final class HttpFileResultConvertHandler implements ConvertHandler {
    //
    //    @Override
    //    public ConvertHandler.Result convert(Object result, Charset charset) {
    //        return result instanceof HttpFileResult httpFileResult
    //                ? ConvertHandler.Result.finished(httpFileResult.createResponse(charset))
    //                : ConvertHandler.Result.shouldContinue();
    //    }
    //}

    ///** 内置环节：认领已是 HttpResponse 的正常反应类型，原样返回不重新包装。 */
    //public static final class HttpResponseConvertHandler implements ConvertHandler {
    //
    //    @Override
    //    public ConvertHandler.Result convert(Object result, Charset charset) {
    //        return result instanceof HttpResponse response
    //                ? ConvertHandler.Result.finished(response)
    //                : ConvertHandler.Result.shouldContinue();
    //    }
    //}

    // ---------- 旧响应体系善后与构建：随响应体系消息化迁移停用 ----------

    ///**
    // * 对转化产出的响应做字符集善后：文件响应补写待编码文件名、HttpInfoResponse 暂存原文落体、
    // * 文本类型 Content-Type 缺 charset 时补写（已声明不覆盖，二进制类型不动）。
    // *
    // * @param response 转化产出的响应
    // * @param charset  装配期判定的缺省字符集
    // * @return 善后的响应本身，供转化出口直接返回
    // */
    //private static HttpResponse applyCharset(HttpResponse response, Charset charset) {
    //    if (response instanceof AbstractFileResponse fileResponse) {
    //        fileResponse.finalizeFilenameEncoding(charset);
    //    }
    //    if (response instanceof HttpInfoResponse infoResponse) {
    //        infoResponse.materialize(charset);
    //    }
    //    stampContentTypeCharset(response, charset);
    //    return response;
    //}

    ///**
    // * Content-Type 为文本类型（text 前缀或 application/json）且未声明非空 charset 时，
    // * 以给定字符集补写 charset 段；头缺失、非文本类型或已声明 charset 的原样保留。
    // *
    // * @param response 响应对象
    // * @param charset  补写用字符集
    // */
    //private static void stampContentTypeCharset(HttpResponse response, Charset charset) {
    //    String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
    //    if (contentType == null || contentType.isBlank()) {
    //        return;
    //    }
    //    String mimeType = contentType.split(";")[0].trim();
    //    boolean textType = mimeType.regionMatches(true, 0, "text/", 0, 5)
    //            || "application/json".equalsIgnoreCase(mimeType);
    //    if (!textType) {
    //        return;
    //    }
    //    for (String part : contentType.split(";")) {
    //        String parameter = part.trim().toLowerCase(Locale.ROOT);
    //        if (parameter.startsWith("charset=") && !parameter.substring("charset=".length()).isEmpty()) {
    //            return;
    //        }
    //    }
    //    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType + ";charset=" + charset.name());
    //}

    ///**
    // * 构建一个HttpResponse，正文按给定字符集编码，Content-Type 由调用点声明完整。
    // *
    // * @param content     内容
    // * @param contentType 内容类型
    // * @param charset     正文编码字符集
    // * @return HttpResponse 响应对象
    // */
    //private static HttpResponse buildResponse(String content, String contentType, Charset charset) {
    //    FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    //    fullHttpResponse.content().writeBytes(content.getBytes(charset));
    //    fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
    //    fullHttpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, fullHttpResponse.content().readableBytes());
    //    return fullHttpResponse;
    //}

    /** 内置环节：认领 null 返回值，产出空 text/html 字节消息。 */
    public static final class NullConvertHandler implements ConvertHandler {

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return result == null
                    ? ConvertHandler.Result.finished(buildByteResponse("", "text/html;charset=" + charset.name(), charset))
                    : ConvertHandler.Result.shouldContinue();
        }
    }

    /** 内置环节：认领 Publisher，转为流式响应消息。 */
    public static final class PublisherConvertHandler implements ConvertHandler {

        private final JsonSerializer jsonSerializer;

        /**
         * 构造 Publisher 转化处理器。
         *
         * @param jsonSerializer json序列化器，流内其他类型元素经其编码，不能为null
         */
        public PublisherConvertHandler(JsonSerializer jsonSerializer) {
            this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        }

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return result instanceof Publisher<?> publisher
                    ? ConvertHandler.Result.finished(toStreamMessage(publisher, charset))
                    : ConvertHandler.Result.shouldContinue();
        }

        /**
         * 将异步流包装为流式响应消息：消息携带 application/octet-stream 内容类型
         * 以满足写出门禁，流内元素按 {@link #encodeElement(Object, Charset)} 逐个编码为字节块。
         *
         * @param publisher 异步流
         * @param charset   装配期判定的缺省字符集
         * @return 流式响应消息
         */
        private StreamResponseMessage toStreamMessage(Publisher<?> publisher, Charset charset) {
            StreamResponseMessage message = new StreamResponseMessage(StatusLine.OK, processPublisher(publisher, charset));
            message.headers().set(CONTENT_TYPE, "application/octet-stream");
            return message;
        }

        /** Content-Type 头名 */
        private static final String CONTENT_TYPE = "Content-Type";

        /**
         * 将异步流逐元素编码为字节数组：String 与 Number 按给定字符集取字节，byte[] 原样透传，
         * ByteBuffer 与 ByteBuf 复制为字节数组，其余对象经 json序列化器编码为json后按该字符集取字节；
         * 元素序列化失败转为流错误信号，不在订阅前的调用点抛出。
         *
         * @param publisher 异步流
         * @param charset   装配期判定的缺省字符集
         * @return 流式响应消息的响应体流
         */
        private Flux<byte[]> processPublisher(Publisher<?> publisher, Charset charset) {
            return Flux.from(publisher)
                    .flatMap(val -> {
                        // 处理类型转化
                        if (val instanceof String s) {
                            return Mono.just(s.getBytes(charset));
                        } else if (val instanceof byte[] bytes) {
                            return Mono.just(bytes);
                        } else if (val instanceof ByteBuffer buffer) {
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.duplicate().get(bytes);
                            return Mono.just(bytes);
                        } else if (val instanceof ByteBuf byteBuf) {
                            return Mono.just(ByteBufUtil.getBytes(byteBuf));
                        } else if (val instanceof Number number) {
                            return Mono.just(number.toString().getBytes(charset));
                        } else {
                            try {
                                String json = jsonSerializer.beanToJson(val);
                                return Mono.just(json.getBytes(charset));
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        }
                    });
        }

        /**
         * 单元素编码：非 JSON 分支的确定性编码，与流内编码共用口径。
         *
         * @param val     元素
         * @param charset 编码字符集
         * @return 字节数组
         */
        private byte[] encodeElement(Object val, Charset charset) {
            if (val instanceof String s) {
                return s.getBytes(charset);
            }
            if (val instanceof byte[] bytes) {
                return bytes;
            }
            if (val instanceof ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.duplicate().get(bytes);
                return bytes;
            }
            if (val instanceof ByteBuf byteBuf) {
                return ByteBufUtil.getBytes(byteBuf);
            }
            if (val instanceof Number number) {
                return number.toString().getBytes(charset);
            }
            return jsonSerializer.beanToJson(val).getBytes(charset);
        }
    }

    /**
     * 构建字节响应消息：正文按给定字符集编码，Content-Type 由调用点声明完整，
     * 内容长度交由写出环节按实际响应体补齐。
     *
     * @param content     内容
     * @param contentType 内容类型
     * @param charset     正文编码字符集
     * @return 字节响应消息
     */
    private static ByteResponseMessage buildByteResponse(String content, String contentType, Charset charset) {
        ByteResponseMessage message = new ByteResponseMessage(StatusLine.OK, content.getBytes(charset));
        message.headers().set(CONTENT_TYPE_HEADER, contentType);
        return message;
    }

    /** Content-Type 头名 */
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    /** 默认环节：按照 application/json 构建，恒认领，兜底内置与自定义都未认领的输入。 */
    public static final class JsonResultConvertHandler implements ConvertHandler {

        private final JsonSerializer jsonSerializer;

        /**
         * 构造 JSON 默认转化处理器。
         *
         * @param jsonSerializer json序列化器，正文经其序列化，不能为null
         */
        public JsonResultConvertHandler(JsonSerializer jsonSerializer) {
            this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        }

        @Override
        public ConvertHandler.Result convert(Object result, Charset charset) {
            return ConvertHandler.Result.finished(buildByteResponse(
                    jsonSerializer.beanToJson(result),
                    "application/json;charset=" + charset.name(), charset));
        }
    }
}
