package top.turboweb.http.testkit;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.http.core.RequestContent;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpUnknowContent;
import top.turboweb.http.orchestrator.HttpContentParserManager;
import top.turboweb.http.orchestrator.RequestOrchestrator;

import java.nio.charset.Charset;
import java.util.List;

/**
 * 上下文契约夹具：按编排器的装配口径把 Netty 请求转化为上下文的对外契约对象，
 * 供需要真实 {@link HttpContext}（而非代理替身）的用例复用。
 *
 * <p>转化口径与生产一致：{@link #content} 按请求头声明的内容类型分派解析器产出请求体内容，
 * HEAD 请求无请求体，以未声明类型的未知内容承载；{@link #message} 再连同请求把内容装配为
 * 请求消息。需要自行指定内容的用例取三参形态。
 *
 * <p>与生产的差异只在解析器管理器按调用现场建立（不持有自定义解析器），
 * 夹具不额外注册自定义解析器；需要钉自定义解析器的用例直接构造
 * {@link HttpContentParserManager}。
 */
public final class Contexts {

    private Contexts() {
    }

    /**
     * 建立请求消息：请求方式、请求头、请求地址与请求体内容，
     * 请求体按请求头声明的内容类型解析，查询参数按给定缺省字符集解码。
     *
     * @param request        请求对象
     * @param defaultCharset 装配期缺省字符集
     * @return 请求消息
     */
    public static HttpRequestMessage message(FullHttpRequest request, Charset defaultCharset) {
        return message(request, content(request, defaultCharset), defaultCharset);
    }

    /**
     * 建立请求消息：请求方式、请求头、请求地址与给定请求体内容，
     * 查询参数按给定缺省字符集解码。
     *
     * @param request        请求对象
     * @param content        请求体内容
     * @param defaultCharset 装配期缺省字符集
     * @return 请求消息
     */
    public static HttpRequestMessage message(FullHttpRequest request, RequestContent content, Charset defaultCharset) {
        return RequestOrchestrator.assembleMessage(request, content, defaultCharset);
    }

    /**
     * 解析请求体为内容对象。
     *
     * @param request        请求对象
     * @param defaultCharset 装配期缺省字符集
     * @return 内容对象；HEAD 请求返回未声明类型的未知内容
     */
    public static RequestContent content(FullHttpRequest request, Charset defaultCharset) {
        if (HttpMethod.HEAD == request.method()) {
            return new HttpUnknowContent(null);
        }
        return new HttpContentParserManager(defaultCharset, List.of()).parse(request);
    }
}
