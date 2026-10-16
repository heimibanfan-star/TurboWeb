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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结果转化器管理器：按内置、自定义、JSON 默认三级次序，将业务返回值转化为待写出的 HTTP 响应。
 * <p>
 * 内置环节按固定次序认领各自支持的返回值类型：HttpResult（委托自身 createResponse）、
 * String（text/html）、HttpFileResult（文件响应）、HttpResponse（原样认领）、
 * null（空 text/html）、Publisher（转 ReactorResponse 流式响应）；
 * 自定义环节经构造器列表传入，按转化器具体类登记到单独容器，内置环节之后按登记次序认领；
 * 两级都不认领的输入由 JSON 默认环节（application/json，恒认领）兜底，保证任意输入都有确定走向。
 * </p>
 * <p>
 * <b>注册约束：</b>自定义转化器为内置类型（含 JSON 默认环节）时构造期抛出
 * {@link IllegalArgumentException}，同一转化器类出现多次抛出 {@link IllegalStateException}，
 * 列表或元素为 null 抛出 {@link NullPointerException}。
 * </p>
 * <p>
 * 转化入口 {@link #convert(Object, Charset)} 携带装配期判定的缺省字符集
 * （响应编码不受请求头声明影响，恒取缺省配置），入参 result 允许为 null：
 * 环节自建响应的正文编码与 Content-Type 声明一律取该字符集；链产出响应后统一善后——
 * 文本类型（text 前缀与 application/json）的 Content-Type 已声明 charset 时不覆盖、
 * 未声明时以该字符集补写，二进制类型不动；构造期未指定编码的文件响应在此以其
 * URL 编码文件名；{@link HttpInfoResponse} 暂存的原文正文在此编码落体。
 * 序列化失败按环节各自契约传导：默认环节在调用点原样上抛，
 * 流内元素转为流错误信号，管理器不吞异常。
 * </p>
 */
public class ResultConverterManager {

    /** 内置转化器类集合（含 JSON 默认环节），自定义注册按具体类拒登内置类型。 */
    private static final Set<Class<? extends ResultConverter>> BUILTIN_CONVERTER_TYPES = Set.of(
            HttpResultConverter.class,
            StringResultConverter.class,
            HttpFileResultConverter.class,
            HttpResponseConverter.class,
            NullResultConverter.class,
            PublisherResultConverter.class,
            JsonResultConverter.class);

    /**
     * 内置结果转化器：构造期一次建定、不可变；环节按声明次序逐个认领，先于自定义环节咨询。
     */
    private final List<ResultConverter> builtinConverters;

    /**
     * 自定义结果转化器容器：构造期由传入列表按具体类登记为不可变表，内置环节之后按登记次序认领。
     */
    private final List<ResultConverter> customConverters;

    /**
     * JSON 默认转化器：内置与自定义两级都未认领时的共同兜底，恒认领。
     */
    private final ResultConverter defaultConverter;

    /**
     * 构造结果转化器管理器并登记自定义转化器。
     *
     * @param jsonSerializer   json序列化器，供 HttpResult 生成响应、流内对象元素编码与默认 JSON 转化使用，不能为null
     * @param customConverters 用户自定义的结果转化器列表，可为空列表但不能为null；
     *                         按转化器具体类登记，每个类至多登记一个实例
     * @throws NullPointerException      序列化管理器或列表为null、列表元素为null
     * @throws IllegalArgumentException  自定义转化器为内置类型（含 JSON 默认环节）
     * @throws IllegalStateException     同一转化器类在列表中出现多次
     */
    public ResultConverterManager(JsonSerializer jsonSerializer, List<ResultConverter> customConverters) {
        Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        Objects.requireNonNull(customConverters, "The custom converter list must not be null");
        this.builtinConverters = List.of(
                new HttpResultConverter(jsonSerializer),
                new StringResultConverter(),
                new HttpFileResultConverter(),
                new HttpResponseConverter(),
                new NullResultConverter(),
                new PublisherResultConverter(jsonSerializer));
        this.customConverters = registerCustomConverters(customConverters);
        this.defaultConverter = new JsonResultConverter(jsonSerializer);
    }

    /**
     * 将业务返回值经内置、自定义两级转化链转化为 HTTP 响应，两级都不认领时由 JSON 默认转化器认领；
     * 产出响应统一做字符集善后（文本类型 Content-Type 缺 charset 时补写、未编码文件名以其 URL 编码、
     * 暂存原文正文以其落体）。
     *
     * @param result  业务返回值，可以为null
     * @param charset 装配期判定的缺省字符集，不能为null
     * @return 转化出的响应，永不为null
     * @throws NullPointerException charset 为 null
     */
    public HttpResponse convert(Object result, Charset charset) {
        Objects.requireNonNull(charset, "The charset must not be null");
        for (ResultConverter converter : this.builtinConverters) {
            ResultConverter.Result converted = converter.convert(result, charset);
            if (converted.status() == ResultConverter.Status.FINISHED) {
                return applyCharset(converted.httpResponse(), charset);
            }
        }
        for (ResultConverter converter : this.customConverters) {
            ResultConverter.Result converted = converter.convert(result, charset);
            if (converted.status() == ResultConverter.Status.FINISHED) {
                return applyCharset(converted.httpResponse(), charset);
            }
        }
        // 执行到默认环节即内置与自定义两级全部放行，默认环节恒认领并收尾
        return applyCharset(this.defaultConverter.convert(result, charset).httpResponse(), charset);
    }

    /**
     * 把自定义转化器列表按具体类登记为不可变表：元素为null拒登，内置类型拒登，同一类重复登记拒登。
     *
     * @param customConverters 用户自定义的结果转化器列表
     * @return 按登记次序排列的不可变转化器列表
     * @throws NullPointerException      列表元素为null
     * @throws IllegalArgumentException  转化器类为内置类型
     * @throws IllegalStateException     同一转化器类出现多次
     */
    private static List<ResultConverter> registerCustomConverters(List<ResultConverter> customConverters) {
        Map<Class<? extends ResultConverter>, ResultConverter> table = new LinkedHashMap<>();
        for (ResultConverter converter : customConverters) {
            Objects.requireNonNull(converter, "The result converter must not be null");
            Class<? extends ResultConverter> converterType = converter.getClass();
            if (BUILTIN_CONVERTER_TYPES.contains(converterType)) {
                throw new IllegalArgumentException(
                        "Can not register a built-in result converter: " + converterType.getName());
            }
            if (table.putIfAbsent(converterType, converter) != null) {
                throw new IllegalStateException("Duplicate result converter registration: " + converterType.getName());
            }
        }
        return List.copyOf(table.values());
    }

    /**
     * 对转化产出的响应做字符集善后：文件响应补写待编码文件名、HttpInfoResponse 暂存原文落体、
     * 文本类型 Content-Type 缺 charset 时补写（已声明不覆盖，二进制类型不动）。
     *
     * @param response 转化产出的响应
     * @param charset  装配期判定的缺省字符集
     * @return 善后的响应本身，供转化出口直接返回
     */
    private static HttpResponse applyCharset(HttpResponse response, Charset charset) {
        if (response instanceof AbstractFileResponse fileResponse) {
            fileResponse.finalizeFilenameEncoding(charset);
        }
        if (response instanceof HttpInfoResponse infoResponse) {
            infoResponse.materialize(charset);
        }
        stampContentTypeCharset(response, charset);
        return response;
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

    /** 内置环节：认领 HttpResult，委托其自身 createResponse 生成响应。 */
    public static final class HttpResultConverter implements ResultConverter {

        private final JsonSerializer jsonSerializer;

        /**
         * 构造 HttpResult 转化器。
         *
         * @param jsonSerializer json序列化器，生成响应时透传，不能为null
         */
        public HttpResultConverter(JsonSerializer jsonSerializer) {
            this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        }

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return result instanceof HttpResult<?> httpResult
                    ? ResultConverter.Result.finished(httpResult.createResponse(jsonSerializer, charset))
                    : ResultConverter.Result.shouldContinue();
        }
    }

    /** 内置环节：认领 String，按照 text/html 构建响应。 */
    public static final class StringResultConverter implements ResultConverter {

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return result instanceof String string
                    ? ResultConverter.Result.finished(buildResponse(string, "text/html;charset=" + charset.name(), charset))
                    : ResultConverter.Result.shouldContinue();
        }
    }

    /** 内置环节：认领 HttpFileResult，按文件响应生成。 */
    public static final class HttpFileResultConverter implements ResultConverter {

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return result instanceof HttpFileResult httpFileResult
                    ? ResultConverter.Result.finished(httpFileResult.createResponse(charset))
                    : ResultConverter.Result.shouldContinue();
        }
    }

    /** 内置环节：认领已是 HttpResponse 的正常反应类型，原样返回不重新包装。 */
    public static final class HttpResponseConverter implements ResultConverter {

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return result instanceof HttpResponse response
                    ? ResultConverter.Result.finished(response)
                    : ResultConverter.Result.shouldContinue();
        }
    }

    /** 内置环节：认领 null 返回值，产出空 text/html 响应。 */
    public static final class NullResultConverter implements ResultConverter {

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return result == null
                    ? ResultConverter.Result.finished(buildResponse("", "text/html;charset=" + charset.name(), charset))
                    : ResultConverter.Result.shouldContinue();
        }
    }

    /** 内置环节：认领 Publisher，转为 ReactorResponse 流式响应。 */
    public static final class PublisherResultConverter implements ResultConverter {

        private final JsonSerializer jsonSerializer;

        /**
         * 构造 Publisher 转化器。
         *
         * @param jsonSerializer json序列化器，流内其他类型元素经其编码，不能为null
         */
        public PublisherResultConverter(JsonSerializer jsonSerializer) {
            this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        }

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return result instanceof Publisher<?> publisher
                    ? ResultConverter.Result.finished(new ReactorResponse(processPublisher(publisher, charset)))
                    : ResultConverter.Result.shouldContinue();
        }

        /**
         * 将异步流逐元素编码为 ByteBuf：String 与 Number 按给定字符集取字节，byte[] 与 ByteBuffer 包装入缓冲，
         * ByteBuf 原样透传，其余对象经 json序列化器编码为json后按该字符集取字节；
         * 元素序列化失败转为流错误信号，不在订阅前的调用点抛出。
         *
         * @param publisher 异步流
         * @param charset   装配期判定的缺省字符集
         * @return Flux&lt;ByteBuf&gt; 响应流
         */
        private Flux<ByteBuf> processPublisher(Publisher<?> publisher, Charset charset) {
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
    }

    /** 默认环节：按照 application/json 构建，恒认领，兜底内置与自定义都未认领的输入。 */
    public static final class JsonResultConverter implements ResultConverter {

        private final JsonSerializer jsonSerializer;

        /**
         * 构造 JSON 默认转化器。
         *
         * @param jsonSerializer json序列化器，正文经其序列化，不能为null
         */
        public JsonResultConverter(JsonSerializer jsonSerializer) {
            this.jsonSerializer = Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        }

        @Override
        public ResultConverter.Result convert(Object result, Charset charset) {
            return ResultConverter.Result.finished(buildResponse(
                    jsonSerializer.beanToJson(result),
                    "application/json;charset=" + charset.name(), charset));
        }
    }
}
