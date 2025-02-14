package org.turbo.utils.http;

import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.core.http.request.HttpInfoRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 用于封装HttpRequest
 */
public class HttpInfoRequestPackageUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpInfoRequestPackageUtils.class);

    private HttpInfoRequestPackageUtils() {
    }

    /**
     * 封装请求对象
     *
     * @param request netty的请求
     * @return 自定义的http请求对象
     */
    public static HttpInfoRequest packageRequest(FullHttpRequest request) {
        Map<String, List<String>> stringStringMap = parseQueryParams(request.uri());
        return new HttpInfoRequest(request);
    }

    private static Map<String, List<String>> parseQueryParams(String uri) {
        Map<String, List<String>> paramsForSearch = new HashMap<>();
        if (uri == null || uri.isEmpty()) {
            return paramsForSearch;
        }
        // 获取搜索参数
        if (uri.contains("?")) {
            String[] split = uri.split("\\?");
            // 处理没有参数的情况
            if (split.length < 2) {
                return paramsForSearch;
            }
            String[] searchParams = split[1].split("&");
            for (String searchParam : searchParams) {
                if (!searchParam.contains("=")) {
                    continue;
                }
                String[] args = searchParam.split("=", 2);
                // 处理参数
                String key, value;
                try {
                    key = URLDecoder.decode(args[0], StandardCharsets.UTF_8);
                    value =URLDecoder.decode(args[1], StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.error("参数解码失败：", e);
                    key = args[0];
                    value = args[1];
                }
                // 保存参数
                paramsForSearch
                    .computeIfAbsent(key, k -> new ArrayList<>(1))
                    .add(value);
            }
        }
        return paramsForSearch;
    }

    public static void main(String[] args) {
        String s = "hello=";
        String[] split = s.split("=", 2);
        System.out.println(Arrays.toString(split));
    }
}
