package top.turboweb.http.processor;

import io.netty.handler.codec.http.*;
import top.turboweb.http.connect.ConnectSession;

import java.util.Arrays;
import java.util.List;

/**
 * 内核处理器：处理跨域（CORS）请求。
 * <p>
 * 该处理器负责根据配置处理 HTTP 请求的跨域逻辑，包括：
 * <ul>
 *     <li>对 OPTIONS 预检请求返回相应的 CORS 响应头</li>
 *     <li>在正常请求响应中添加 CORS 响应头</li>
 * </ul>
 * </p>
 * <p>
 * CORS 配置可通过 {@link Config} 进行定制，包括允许的域、方法、头信息、是否允许携带凭证、暴露的头和缓存时间等。
 * </p>
 */
public class CorsProcessor extends Processor {

    private final Config config = new Config();

    /**
     * CORS 配置类
     */
    public static class Config {
        private List<String> allowedOrigins = List.of("*");
        private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE");
        private List<String> allowedHeaders = List.of("*");
        private List<String> exposedHeaders = List.of("Content-Disposition");
        private boolean allowCredentials = false;
        private int maxAge = 3600;

        /** 设置允许的跨域源 */
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        /** 设置允许的跨域请求方法 */
        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }
        /** 设置允许的请求头 */

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }
        /** 设置可暴露的响应头 */

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }
        /** 设置是否允许携带凭证 */

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
        /** 设置预检请求缓存时间（秒） */

        public void setMaxAge(int maxAge) {
            this.maxAge = maxAge;
        }
    }

    /**
     * 处理 HTTP 请求。
     * <p>
     * 对 OPTIONS 请求返回带有 CORS 响应头的空响应；
     * 对非 OPTIONS 请求先调用链中下一个处理器生成响应，再添加 CORS 响应头。
     * </p>
     *
     * @param fullHttpRequest 请求对象
     * @param connectSession  当前连接会话
     * @return 包含 CORS 响应头的 HTTP 响应对象
     */
    @Override
    public HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
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
        HttpResponse httpResponse = next(fullHttpRequest, connectSession);
        // 设置 CORS 响应头
        handleCorsHeaders(httpResponse, origin);
        return httpResponse;
    }

    /**
     * 为响应对象设置 CORS 响应头。
     *
     * @param response 响应对象
     * @param origin   请求的 Origin
     */
    private void handleCorsHeaders(HttpResponse response, String origin) {
        if (config.allowCredentials) {
            if ("*".equals(origin)) {
                // CORS 标准禁止 Access-Control-Allow-Credentials 与 * 一起出现
                return;
            }
            response.headers().set("Access-Control-Allow-Credentials", "true");
        }
        // 检查是否允许跨域
        if (config.allowedOrigins.contains("*") || config.allowedOrigins.contains(origin)) {
            // 设置 CORS 响应头
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Methods", config.allowedMethods.contains("*")? "*": String.join(",", config.allowedMethods));
            response.headers().set("Access-Control-Allow-Headers", config.allowedHeaders.contains("*")? "*": String.join(",", config.allowedHeaders));
            response.headers().set("Access-Control-Expose-Headers", config.exposedHeaders.contains("*")? "*": String.join(",", config.exposedHeaders));
            response.headers().set("Access-Control-Max-Age", String.valueOf(config.maxAge));
        }
    }

    /**
     * 获取 CORS 配置对象，可用于修改默认配置
     *
     * @return CORS 配置对象
     */
    public Config getConfig() {
        return config;
    }

}
