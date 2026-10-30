package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.core.Content;
import top.turboweb.http.core.ContentType;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * http内容解析器：按请求体类型把请求解析为 {@link Content}。
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
     * <p>解析必须在本调用内完全消费请求体：编排流程在请求消息装配完成后立即释放请求对象，
     * 请求体内存随之回收；实现不得持有请求引用、不得在本调用返回后延迟读取请求体。</p>
     *
     * @param request        待解析的请求对象
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码
     * @return 解析出的内容对象
     */
    Content parse(FullHttpRequest request, Charset defaultCharset);
}
