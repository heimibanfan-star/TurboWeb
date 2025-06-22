package top.turboweb.http.processor;

import io.netty.handler.codec.http.*;

import java.util.Arrays;
import java.util.List;

/**
 * 处理跨域请求的内核处理器
 */
public class CorsProcessor extends Processor {

    private List<String> allowedOrigins = List.of("*");
    private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE");
    private List<String> allowedHeaders = List.of("*");
    private List<String> exposedHeaders = List.of("Content-Disposition");
    private boolean allowCredentials = false;
    private int maxAge = 3600;

    public CorsProcessor(Processor nextProcessor) {
        super(nextProcessor);
    }

    @Override
    public HttpResponse invoke(FullHttpRequest fullHttpRequest) {
        // 获取请求头中的origin
        String origin = fullHttpRequest.headers().get("Origin");
        if (origin == null || origin.isEmpty()) {
            origin = "null";
        }
        // 处理OPTIONS请求
        if ("OPTIONS".equalsIgnoreCase(fullHttpRequest.method().name())) {
            FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            handleCorsHeaders(fullHttpResponse, origin);
            return fullHttpResponse;
        }

        // 调用后续的中间件
        HttpResponse httpResponse = next(fullHttpRequest);
        // 设置 CORS 响应头
        handleCorsHeaders(httpResponse, origin);
        return httpResponse;
    }

    /**
     * 设置 CORS 响应头
     * @param response 响应对象
     * @param origin 请求的 origin
     */
    private void handleCorsHeaders(HttpResponse response, String origin) {
        if (allowCredentials) {
            if ("*".equals(origin)) {
                // CORS 标准禁止 Access-Control-Allow-Credentials 与 * 一起出现
                return;
            }
            response.headers().set("Access-Control-Allow-Credentials", "true");
        }
        // 检查是否允许跨域
        if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
            // 设置 CORS 响应头
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Methods", allowedMethods.contains("*")? "*": String.join(",", allowedMethods));
            response.headers().set("Access-Control-Allow-Headers", allowedHeaders.contains("*")? "*": String.join(",", allowedHeaders));
            response.headers().set("Access-Control-Expose-Headers", exposedHeaders.contains("*")? "*": String.join(",", exposedHeaders));
            response.headers().set("Access-Control-Max-Age", String.valueOf(maxAge));
        }
    }

    // 提供 setter 方法来允许外部配置这些属性
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

}
