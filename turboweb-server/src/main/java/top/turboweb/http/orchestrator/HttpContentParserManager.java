package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpUnknowContent;
import top.turboweb.http.core.constant.ContentType;
import top.turboweb.http.core.helper.HttpRequestHelper;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code HttpContentParserManager} 按请求头声明的 Content-Type 把请求体路由给内容解析器，
 * 产出 {@link HttpContext.Content}。
 *
 * <p><b>路由顺序：</b>请求头有类型声明时，先遍历内置解析器（内置数量少，线性遍历优于哈希查找），
 * 未命中再查自定义解析器表（按归一化 MIME 类型哈希定位）；两者皆未命中、或请求头未声明类型时，
 * 返回 {@link HttpUnknowContent}——携带头声明的类型，无声明时类型为 {@code null}。
 * 命中解析器后请求体无效（如 JSON 体外非花括号包裹）时异常原样传播，不回落未知内容。
 *
 * <p><b>注册约束：</b>自定义解析器的类型与内置类型冲突、或自定义之间重复注册同一类型时，
 * 构造期抛出 {@link IllegalArgumentException}。类型匹配一律转小写后比较（大小写不敏感），
 * MIME 类型之外的编码段（charset 等）不参与路由。
 *
 * <p><b>缺省字符集：</b>装配期判定一次并持有，随每次解析透传给命中的解析器，
 * 由各内容对象按"请求头声明优先、缺省回落"口径解码请求体。
 *
 * <p>本管理器构造后只读，可跨请求共享、多线程并发调用；产出的内容对象按请求单线程使用，
 * 其中持有资源的实现（{@link HttpContext.AutoCloseContent}）由持有方在请求处理结束后关闭。
 */
public class HttpContentParserManager {

    /** 内置内容解析器：数量少，路由与冲突判定均直接遍历 */
    private final List<HttpContentParser> builtinParsers;

    /** 自定义内容解析器：键为归一化（转小写）的 MIME 类型，构造期建立后只读 */
    private final Map<String, HttpContentParser> customParsers;

    /** 装配期判定的缺省解码字符集，随每次解析透传给命中的解析器 */
    private final Charset defaultCharset;

    /**
     * 建立解析器管理器并注册自定义解析器。
     *
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码，不能为空
     * @param customParsers  用户自定义的解析器列表，可为空列表但不能为 {@code null}
     * @throws NullPointerException      当 {@code defaultCharset} 或 {@code customParsers} 为 {@code null} 时抛出
     * @throws IllegalArgumentException  当自定义解析器声明的类型与内置类型冲突，或多个自定义解析器声明同一类型时抛出
     */
    public HttpContentParserManager(Charset defaultCharset, List<HttpContentParser> customParsers) {
        this.defaultCharset = Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
        Objects.requireNonNull(customParsers, "customParsers can not be null");
        this.builtinParsers = List.of(new FormContentParser(), new JsonContentParser());

        Set<String> builtinTypes = new HashSet<>();
        for (HttpContentParser parser : this.builtinParsers) {
            for (ContentType type : parser.contentTypes()) {
                builtinTypes.add(normalize(type.getMimeType()));
            }
        }
        Map<String, HttpContentParser> registered = new HashMap<>();
        for (HttpContentParser parser : customParsers) {
            for (ContentType type : parser.contentTypes()) {
                String mimeType = normalize(type.getMimeType());
                if (builtinTypes.contains(mimeType)) {
                    throw new IllegalArgumentException(
                            "custom parser content type " + mimeType + " conflicts with builtin parser");
                }
                if (registered.putIfAbsent(mimeType, parser) != null) {
                    throw new IllegalArgumentException("duplicate custom parser for content type " + mimeType);
                }
            }
        }
        this.customParsers = registered;
    }

    /**
     * 按请求头声明的内容类型解析请求体。
     *
     * @param request 待解析的请求对象，不能为空
     * @return 命中的解析器产出的内容对象；无匹配类型或请求头未声明类型时返回 {@link HttpUnknowContent}
     * @throws NullPointerException  当 {@code request} 为 {@code null} 时抛出
     * @throws top.turboweb.http.exception.RequestParseException 命中解析器后请求体无效时由解析器抛出，原样传播
     */
    public HttpContext.Content parse(FullHttpRequest request) {
        Objects.requireNonNull(request, "request can not be null");
        String mimeType = HttpRequestHelper.getContentType(request);
        if (mimeType == null) {
            return new HttpUnknowContent(null);
        }
        String key = normalize(mimeType);
        for (HttpContentParser parser : builtinParsers) {
            if (supports(parser, key)) {
                return parser.parse(request, defaultCharset);
            }
        }
        HttpContentParser custom = customParsers.get(key);
        if (custom != null) {
            return custom.parse(request, defaultCharset);
        }
        return new HttpUnknowContent(ContentType.create(mimeType));
    }

    /**
     * 判断解析器是否承接归一化后的 MIME 类型。
     */
    private static boolean supports(HttpContentParser parser, String normalizedMimeType) {
        for (ContentType type : parser.contentTypes()) {
            if (normalize(type.getMimeType()).equals(normalizedMimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一化 MIME 类型：转小写，作为路由键与注册冲突判定口径，与内容层的
     * 大小写不敏感类型比较同口径。
     */
    private static String normalize(String mimeType) {
        return mimeType.toLowerCase(Locale.ROOT);
    }
}
