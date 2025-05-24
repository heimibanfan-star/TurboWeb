package org.turboweb.core.http.request;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.*;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.http.cookie.Cookies;
import org.turboweb.commons.exception.TurboHttpParseException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 用于封装HttpRequest
 */
public class HttpInfoRequestPackageHelper {

    private static final Logger log = LoggerFactory.getLogger(HttpInfoRequestPackageHelper.class);

    private static final Map<String, Function<FullHttpRequest, HttpContent>> bodyInfoParseFunctions = new ConcurrentHashMap<>();

    private static Charset charset = StandardCharsets.UTF_8;

    static {
        bodyInfoParseFunctions.put("application/json", HttpInfoRequestPackageHelper::doParseJsonBodyInfo);
        bodyInfoParseFunctions.put("application/x-www-form-urlencoded", HttpInfoRequestPackageHelper::doParseFormBodyInfo);
        bodyInfoParseFunctions.put("multipart/form-data", HttpInfoRequestPackageHelper::doParseFormBodyInfo);
    }

    private HttpInfoRequestPackageHelper() {
    }

    public static void setCharset(Charset charset) {
        HttpInfoRequestPackageHelper.charset = charset;
    }

    /**
     * 封装请求对象
     *
     * @param request netty的请求
     * @return 自定义的http请求对象
     */
    public static HttpInfoRequest packageRequest(FullHttpRequest request) {
        Map<String, List<String>> queryParams = parseQueryParams(request.uri());
        Cookies cookies = initCookies(request);
        HttpContent content = null;
        // 获取请求方式
        String method = request.method().name();
        if (HttpMethod.POST.name().equals(method) || HttpMethod.PUT.name().equals(method) || HttpMethod.PATCH.name().equals(method)) {
            if (request.headers().get(HttpHeaderNames.CONTENT_TYPE) != null) {
                // 只有当请求方式为post、put、patch时，并且请求体不为空时，才进行解析
                content = parseBodyInfo(request);
            } else {
                content = HttpContent.empty();
            }
        } else {
            content = HttpContent.empty();
        }
        return new HttpInfoRequest(request, cookies, queryParams, content);
    }

    /**
     * 初始化cookies
     *
     * @param request 请求对象
     * @return cookies
     */
    private static Cookies initCookies(FullHttpRequest request) {
        Map<String, String> cookies = new HashMap<>();
        String cookie = request.headers().get(HttpHeaderNames.COOKIE);
        if (cookie != null) {
            String[] cookieArray = cookie.split(";");
            for (String s : cookieArray) {
                String[] cookieItem = s.split("=");
                if (cookieItem.length == 2) {
                    String key = cookieItem[0];
                    String value = cookieItem[1];
                    cookies.put(key != null ? key.trim() : "", value != null ? value.trim() : "");
                }
            }
        }
        return new Cookies(cookies);
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

    /**
     * 解析请求体
     *
     * @param request 请求对象
     * @return 请求体
     */
    private static HttpContent parseBodyInfo(FullHttpRequest request) {
        // 获取请求的格式
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            contentType = contentType.split(";")[0];
        }
        // 判断是否是支持的格式
        if (!bodyInfoParseFunctions.containsKey(contentType)) {
            throw new TurboHttpParseException("不支持的请求体格式:" + contentType);
        }
        // 获取请求处理器
        Function<FullHttpRequest, HttpContent> function = bodyInfoParseFunctions.get(contentType);
        // 处理请求信息
        return function.apply(request);
    }

    /**
     * 封装json格式的请求体
     *
     * @param request 请求对象
     * @return 请求体
     */
    private static HttpContent doParseJsonBodyInfo(FullHttpRequest request) {
        HttpContent content = new HttpContent();
        // 获取请求体的内容
        ByteBuf contentBuf = request.content();
        // 将请求体转化为字符串
        String jsonContent = contentBuf.toString(charset);
        if (jsonContent == null || jsonContent.isBlank()) {
            jsonContent = "{}";
        }
        // 判断是否是json格式
        if (jsonContent.startsWith("{") && jsonContent.endsWith("}")) {
            content.setJsonContent(jsonContent);
            content.setContentType("application/json");
            return content;
        }
        throw new TurboHttpParseException("请求体不是json格式");
    }

    /**
     * 封装form格式的请求体
     *
     * @param request 请求对象
     * @return 请求体
     */
    private static HttpContent doParseFormBodyInfo(FullHttpRequest request) {
        HttpContent content = new HttpContent();
        Map<String, List<String>> formParams = new HashMap<>();
        Map<String, List<FileUpload>> formFiles = new HashMap<>();
        // 创建处理器
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(DefaultHttpDataFactory.MAXSIZE), request);
        List<InterfaceHttpData> httpDataList = decoder.getBodyHttpDatas();
        // 解析数据
        for (InterfaceHttpData httpData : httpDataList) {
            if (httpData instanceof Attribute attribute) {
                String name = attribute.getName();
                String value;
                try {
                    value = attribute.getValue();
                    if (name == null || name.isBlank()) {
                        log.error("form表单中出现空name：key:{}, value: {}", name, value);
                        continue;
                    }
                } catch (IOException e) {
                    log.error("解析失败", e);
                    throw new TurboHttpParseException("解析请求体失败");
                } finally {
                    attribute.release();
                }
                // 加入到集合中
                formParams.computeIfAbsent(name, k -> new ArrayList<>(1)).add(value);
            } else if (httpData instanceof FileUpload fileUpload) {
                // 获取文件名
                String name = fileUpload.getName();
                if (name == null || name.isBlank()) {
                    log.error("文件上传出现空的name：name:{}", name);
                }
                // 存入请求信息中
                formFiles.computeIfAbsent(name, k -> new ArrayList<>(1)).add(fileUpload);
            }
        }
        // 封装内容
        content.setFormParams(formParams);
        content.setFormFiles(formFiles);
        // 判断表单的类型
        if (decoder.isMultipart()) {
            content.setContentType("multipart/form-data");
        } else {
            content.setContentType("application/x-www-form-urlencoded");
        }
        return content;
    }
}
