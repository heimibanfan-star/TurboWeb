package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Objects;

/**
 * http请求相关的工具包
 */
public class HttpRequestHelper {

    /**
     * 获取请求的格式
     * @param request 请求对象
     * @return 请求格式；Content-Type 头缺失或为空白时返回 null（与 {@link #getRequestCharset} 的空白视同缺失口径一致）
     */
    public static String getContentType(FullHttpRequest request) {
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.split(";")[0];
    }

    /**
     * 从 FullHttpRequest 中提取请求体编码：请求头 Content-Type 显式声明 charset 时优先采用，
     * 未声明（头缺失、无 charset 段、charset 名为空白或不可识别）时回落到给定的缺省编码
     * @param request FullHttpRequest 对象，为 null 时直接返回 fallback
     * @param fallback 缺省编码（装配期判定并随请求传递），不能为 null
     * @return 请求体字符编码
     * @throws NullPointerException fallback 为 null
     */
    public static Charset getRequestCharset(FullHttpRequest request, Charset fallback) {
        Objects.requireNonNull(fallback, "fallback can not be null");
        if (request == null) {
            return fallback;
        }

        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            // contentType 形如 "application/json; charset=UTF-8"
            String[] parts = contentType.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    String charsetName = part.substring("charset=".length()).trim();
                    if (charsetName.isEmpty()) {
                        continue;
                    }
                    try {
                        return Charset.forName(charsetName);
                    } catch (Exception ignore) {
                        // 继续尝试其他部分，或者最终返回默认
                    }
                }
            }
        }
        // 没指定 charset，回落缺省编码
        return fallback;
    }
}
