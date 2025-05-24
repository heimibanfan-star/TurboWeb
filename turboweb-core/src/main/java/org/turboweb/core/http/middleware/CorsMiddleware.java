package org.turboweb.core.http.middleware;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.turboweb.core.http.context.HttpContext;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public class CorsMiddleware extends Middleware {

    private List<String> allowedOrigins = List.of("*");
    private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE");
    private List<String> allowedHeaders = List.of("*");
    private List<String> exposedHeaders = List.of("Content-Disposition");
    private boolean allowCredentials = false;
    private int maxAge = 3600;

    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求头中的 Origin
        String origin = ctx.getRequest().getHeaders().get("Origin");
        if (origin == null || origin.isEmpty()) {
            origin = "*";
        }
        // 如果是 OPTIONS 请求，直接返回 200，表示允许跨域
        if ("OPTIONS".equalsIgnoreCase(ctx.getRequest().getMethod())) {
            setCorsHeaders(ctx.getResponse(), origin);
            return ctx.getResponse().setStatus(HttpResponseStatus.OK);
        }
        try {
            Object result = next(ctx);
            if (!ctx.isWrite() && result instanceof HttpResponse httpResponse) {
                setCorsHeaders(httpResponse, origin);
                return httpResponse;
            }
            // 处理反应式的结果
            if (result instanceof Mono<?> mono) {
                String tempOrigin = origin;
                return mono.map(res -> {
                   if (res instanceof HttpResponse response) {
                       setCorsHeaders(response, tempOrigin);
                   }
                   return res;
                });
            }
            return result;
        } finally {
            setCorsHeaders(ctx.getResponse(), origin);
        }
    }

    private void setCorsHeaders(HttpResponse response, String origin) {
        // 检查是否允许跨域
        if (allowedOrigins.contains("*") || allowedOrigins.contains(origin)) {
            // 设置 CORS 响应头
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Methods", allowedMethods.contains("*")? "*": String.join(",", allowedMethods));
            response.headers().set("Access-Control-Allow-Headers", allowedHeaders.contains("*")? "*": String.join(",", allowedHeaders));
            response.headers().set("Access-Control-Expose-Headers", exposedHeaders.contains("*")? "*": String.join(",", exposedHeaders));
            response.headers().set("Access-Control-Max-Age", String.valueOf(maxAge));
            if (allowCredentials) {
                response.headers().set("Access-Control-Allow-Credentials", "true");
            }
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

