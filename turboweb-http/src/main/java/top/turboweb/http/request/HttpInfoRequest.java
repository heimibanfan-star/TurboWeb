package top.turboweb.http.request;

import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.cookie.Cookies;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;

import java.util.List;
import java.util.Map;

/**
 * 封装http请求信息
 */
public class HttpInfoRequest {

    private final Logger log = LoggerFactory.getLogger(HttpInfoRequest.class);


    private final FullHttpRequest request;
    private final Cookies cookies;
    private HttpSession httpSession;
    private final Map<String, List<String>> queryParams;
    private final HttpContent content;
    private final String contentType;

    public HttpInfoRequest(FullHttpRequest request, Cookies cookies, Map<String, List<String>> queryParams, HttpContent content) {
        this.request = request;
        this.cookies = cookies;
        this.queryParams = queryParams;
        this.content = content;
        if (content != null) {
            this.contentType = content.getContentType();
        } else {
            this.contentType = null;
        }
    }

    public FullHttpRequest getRequest() {
        return request;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public HttpContent getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
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
     * 判断session是否为空
     *
     * @return true:session为空
     */
    public boolean sessionIsNull() {
        return httpSession == null;
    }

    /**
     * 获取请求头
     *
     * @return 请求头
     */
    public HttpHeaders getHeaders() {
        return request.headers();
    }

    /**
     * 获取请求cookie
     *
     * @return 请求cookie
     */
    public Cookies getCookies() {
        return cookies;
    }
}
