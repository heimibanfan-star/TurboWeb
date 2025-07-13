package top.turboweb.http.request;

import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 封装http请求信息
 */
public class HttpInfoRequest {

    private final Logger log = LoggerFactory.getLogger(HttpInfoRequest.class);


    private final FullHttpRequest request;
    private Map<String, List<String>> queryParams;
    private HttpContent content;

    public HttpInfoRequest(FullHttpRequest request) {
        this.request = request;
    }

    public FullHttpRequest getRequest() {
        return request;
    }

    public Map<String, List<String>> getQueryParams() {
        if (queryParams == null) {
            this.queryParams = HttpRequestDecoder.parseQueryParams(request.uri());
        }
        return queryParams;
    }

    public HttpContent getContent() {
        if (this.content != null) {
            return this.content;
        }
        this.content = HttpRequestDecoder.parseBodyInfo(request);
        return this.content;
    }

    public String getContentType() {
        return this.getContent().getContentType();
    }

    public HttpVersion getProtocolVersion() {
        return request.protocolVersion();
    }

    /**
     * 获取请求方法
     *
     * @return 请求方法
     */
    public String getMethod() {
        return request.method().name();
    }

    /**
     * 获取请求路径
     *
     * @return 请求路径
     */
    public String getUri() {
        return request.uri();
    }

    public Logger getLog() {
        return log;
    }

    /**
     * 获取请求头
     *
     * @return 请求头
     */
    public HttpHeaders getHeaders() {
        return request.headers();
    }
}
