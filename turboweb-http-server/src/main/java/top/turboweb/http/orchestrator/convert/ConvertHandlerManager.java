package top.turboweb.http.orchestrator.convert;

import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结果转化处理器管理器：按内置、自定义两级转化链将业务返回值转化为待写出的响应消息，收尾由管理器降级方法兜底。
 * <p>
 * 内置环节按固定次序认领各自支持的返回值类型：HttpResult（装配为字节消息）、
 * String（text/html）、Publisher（转 StreamResponseMessage 流式消息）；
 * 自定义环节经构造器列表传入，按处理器具体类登记到单独容器，内置环节之后按登记次序认领，
 * null 返回值内置环节均放行，自定义环节可见并有机会认领；
 * 两级都不认领的输入由管理器降级收尾：已是响应消息的直接透传，
 * null 产出空 text/html，其余按 application/json 输出，保证任意输入都有确定走向。
 * </p>
 * <p>
 * <b>注册约束：</b>自定义处理器为内置类型时构造期抛出
 * {@link IllegalArgumentException}，同一处理器类出现多次抛出 {@link IllegalStateException}，
 * 列表或元素为 null 抛出 {@link NullPointerException}。
 * </p>
 * <p>
 * 转化入口 {@link #convert(Object, Charset)} 携带装配期判定的缺省字符集
 * （响应编码不受请求头声明影响，恒取缺省配置），入参 result 允许为 null：
 * 环节自建消息的正文编码与 Content-Type 声明一律取该字符集，
 * 内容长度由写出环节按实际响应体补齐。
 * 序列化失败按环节各自契约传导：降级收尾在调用点原样上抛，
 * 流内元素转为流错误信号，管理器不吞异常。
 * </p>
 */
public class ConvertHandlerManager {

    /** 内置处理器类集合，自定义注册按具体类拒登内置类型。 */
    private static final Set<Class<? extends ConvertHandler>> BUILTIN_CONVERTER_TYPES = Set.of(
            HttpResultConvertHandler.class,
            StringConvertHandler.class,
            PublisherConvertHandler.class);

    /**
     * 内置结果转化处理器：构造期一次建定、不可变；环节按声明次序逐个认领，先于自定义环节咨询。
     */
    private final List<ConvertHandler> builtinConverters;
    /**
     * 自定义结果转化处理器：按登记次序排列的不可变列表，内置环节之后逐个咨询。
     */
    private final List<ConvertHandler> customConverters;
    /**
     * json序列化器：降级收尾的非消息输入与流内其他类型元素编码经其序列化。
     */
    private final JsonSerializer jsonSerializer;

    /**
     * 构造结果转化处理器管理器。
     *
     * @param jsonSerializer  json序列化器，降级收尾与流内对象元素编码经其序列化，不能为null
     * @param customConverters 自定义结果转化处理器列表，不能为null、元素不能为null
     * @throws NullPointerException     json序列化器或自定义列表为null、列表元素为null
     * @throws IllegalArgumentException 自定义处理器为内置类型
     * @throws IllegalStateException    同一处理器类在列表中出现多次
     */
    public ConvertHandlerManager(JsonSerializer jsonSerializer, List<ConvertHandler> customConverters) {
        Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
        Objects.requireNonNull(customConverters, "The custom convert handler list must not be null");
        this.jsonSerializer = jsonSerializer;
        this.builtinConverters = List.of(
                new HttpResultConvertHandler(jsonSerializer),
                new StringConvertHandler(),
                new PublisherConvertHandler(jsonSerializer));
        this.customConverters = registerCustomConverters(customConverters);
    }

    /**
     * 将业务返回值经内置、自定义两级转化链转化为响应消息，两级都不认领时由管理器降级收尾兜底；
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
        // 执行到降级收尾即内置与自定义两级全部放行，由管理器兜底保证任意输入都有确定走向
        return fallbackConvert(result, charset);
    }

    /**
     * 降级收尾：兜底内置与自定义环节全部放行的输入。
     * 已是响应消息的直接透传，交由写出环节的门禁与路由接管；
     * null 产出空 text/html 字节消息；其余输入经 json 序列化器按 application/json 装配，
     * 序列化失败原样上抛，管理器不吞异常。
     *
     * @param result  业务返回值，可能为null
     * @param charset 装配期判定的缺省字符集
     * @return 转化出的响应消息，永不为null
     */
    private HttpResponseMessage<?> fallbackConvert(Object result, Charset charset) {
        if (result instanceof HttpResponseMessage<?> message) {
            return message;
        }
        if (result == null) {
            return buildByteResponse("", "text/html;charset=" + charset.name(), charset);
        }
        return buildByteResponse(jsonSerializer.beanToJson(result),
                "application/json;charset=" + charset.name(), charset);
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
}
