package org.turboweb.utils.http;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Map;

/**
 * http响应对象的工具类
 */
public class HttpResponseUtils {

    /**
     * 合并两个HttpResponse对象的头部
     *
     * @param source 源响应对象
     * @param target 目标响应对象
     */
    public static void mergeHeaders(HttpResponse source, HttpResponse target) {
        // 遍历源响应的所有头部
        for (Map.Entry<String, String> entry : source.headers()) {
            String headerName = entry.getKey();
            String headerValue = entry.getValue();

            // 如果目标响应已经包含该头部
            if (target.headers().contains(headerName)) {
                // 特殊处理 Set-Cookie 头部，因为该头部允许多个值
                if (headerName.equalsIgnoreCase("Set-Cookie")) {
                    // 如果目标响应已经有 Set-Cookie 头部，则添加新的 Set-Cookie 值
                    target.headers().add(headerName, headerValue);
                }
                // 对于 CORS 相关头部，确保不会覆盖用户自定义的 CORS 配置
                else if (headerName.equalsIgnoreCase("Access-Control-Allow-Origin")
                    || headerName.equalsIgnoreCase("Access-Control-Allow-Methods")
                    || headerName.equalsIgnoreCase("Access-Control-Allow-Headers")
                    || headerName.equalsIgnoreCase("Access-Control-Allow-Credentials")) {
                    // 如果已经有了相应的 CORS 头部，则不覆盖
                    continue;
                }
                else if (headerName.equalsIgnoreCase("Content-Type")
                    || headerName.equalsIgnoreCase("Content-Length")) {
                    continue;
                }
            }

            // 如果目标响应没有该头部，或者不受特殊处理的头部，则直接添加
            target.headers().set(headerName, headerValue);
        }
    }
}
