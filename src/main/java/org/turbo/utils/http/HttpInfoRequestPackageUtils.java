package org.turbo.utils.http;

import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.core.http.request.HttpInfoRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
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

    /**
     * 解析url中的参数
     *
     * @param uri uri地址
     * @return 参数map
     */
    private static Map<String, List<String>> parseQueryParams(String uri) {
        Map<String, List<String>> paramsForSearch = new HashMap<>();
        try {
            URIBuilder uriBuilder = new URIBuilder(uri);
            // 获取所有的查询参数
            List<NameValuePair> params = uriBuilder.getQueryParams();
            for (NameValuePair param : params) {
                paramsForSearch
                    .computeIfAbsent(param.getName(), k -> new ArrayList<>(1))
                    .add(param.getValue());
            }
        } catch (Exception e) {
            log.error("解析url参数失败", e);
        }
        return paramsForSearch;
    }
}
