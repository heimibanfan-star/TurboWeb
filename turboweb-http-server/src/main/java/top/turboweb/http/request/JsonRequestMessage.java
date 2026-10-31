package top.turboweb.http.request;

import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.RequestLine;

import java.util.Objects;

/**
 * JSON 请求消息：请求体以解析产出的 JSON 字符串承载，
 * 内容对象由 {@code JsonContentParser} 解析产出，本类只做持有，各访问器重复访问返回同一值。
 *
 * <p>请求方式与请求目标由请求行承接；头部读写由 {@link HttpRequestMessage} 的基类承载。</p>
 */
public class JsonRequestMessage extends HttpRequestMessage<String> {

    /**
     * 建立 JSON 请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @param content     解析产出的 JSON 请求体字符串，不能为空
     * @throws NullPointerException 当 {@code requestLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public JsonRequestMessage(RequestLine requestLine, String content) {
        super(requestLine, content);
    }

    /**
     * 获取解析产出的 JSON 请求体字符串。
     *
     * @return 去空白、空体已补 {@code {}} 的字符串本身
     */
    public String jsonString() {
        return this.content();
    }
}
