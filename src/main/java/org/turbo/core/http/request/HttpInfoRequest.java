package org.turbo.core.http.request;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 封装http请求信息
 */
public class HttpInfoRequest {

    private final Logger log = LoggerFactory.getLogger(HttpInfoRequest.class);


    private final FullHttpRequest request;
    // 用户存储搜索参数
    private final Map<String, Object> paramsForSearch = new HashMap<>();
    // 用户存储body参数
    private final Map<String, Object> paramsForBody = new HashMap<>();
    // 用户存储文件
    private final Map<String, FileUpload> files = new HashMap<>();

    public HttpInfoRequest(FullHttpRequest request) {
        this.request = request;
        handleSearchParam();
//        handleMultipart();
    }

    /**
     * 处理搜索参数
     */
    private void handleSearchParam() {
        // 获取请求路径
        String uri = request.uri();

    }

//    private void handleMultipart() {
//        if (!HttpPostRequestDecoder.isMultipart(request)) {
//            return;
//        }
//        // 处理多部份表单
//        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(DefaultHttpDataFactory.MAXSIZE), request);
//        while (decoder.hasNext()) {
//            InterfaceHttpData httpData = decoder.next();
//            if (httpData instanceof Attribute) {
//                Attribute attribute = (Attribute) httpData;
//                try {
//                    System.out.println(attribute.getName() + ":" + attribute.getValue());
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            } else if (httpData instanceof FileUpload) {
//                FileUpload fileUpload = (FileUpload) httpData;
//                String name = fileUpload.getName();
//            }
//        }
//
//    }

    /**
     * 获取请求头
     *
     * @return 请求头
     */
    public HttpHeaders getHeaders() {
        return request.headers();
    }

    /**
     * 获取请求头
     *
     * @param name 请求头名称
     * @return 请求头
     */
    public String getHeader(String name) {
        return request.headers().get(name);
    }

    /**
     * 获取请求的uri
     *
     * @return 请求uri
     */
    public String getUri() {
        return request.uri();
    }

    /**
     * 获取cookie
     *
     * @return cookie
     */
    public String getCookie(String name) {
        return request.headers().get(HttpHeaderNames.COOKIE);
    }

    /**
     * 获取请求方式
     *
     * @return java.lang.String
     */
    public String getMethod() {
        return request.method().name();
    }
}
