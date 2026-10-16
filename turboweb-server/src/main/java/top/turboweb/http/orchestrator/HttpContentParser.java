package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.constant.ContentType;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * http内容解析器：按请求体类型把请求解析为 {@link HttpContext.Content}。
 *
 * <p>实现类声明自己承接的内容类型集合（一个解析器可覆盖多个类型），
 * 由 {@link HttpContentParserManager} 按请求头声明的类型路由调用。
 */
public interface HttpContentParser {

    /**
     * 声明当前解析器支持的请求体类型。
     *
     * @return 支持的类型集合，至少一个；集合内的 MIME 类型用于路由匹配与注册冲突判定
     */
    Set<ContentType> contentTypes();

    /**
     * 解析请求体为内容对象。
     *
     * @param request        待解析的请求对象
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码
     * @return 解析出的内容对象
     */
    HttpContext.Content parse(FullHttpRequest request, Charset defaultCharset);
}
