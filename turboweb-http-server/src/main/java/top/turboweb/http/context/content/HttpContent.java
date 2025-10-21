package top.turboweb.http.context.content;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboHttpParseException;
import top.turboweb.commons.utils.base.HttpRequestUtils;

import java.io.IOException;
import java.util.*;

/**
 * {@code HttpContent} 表示 HTTP 请求体的内容。
 * <p>
 * 支持 JSON 请求体、表单（application/x-www-form-urlencoded）请求体以及
 * 文件上传（multipart/form-data）请求体。
 * <p>
 * 注意：
 * <ul>
 *     <li>JSON 内容只在 Content-Type 为 {@code application/json} 时可用。</li>
 *     <li>表单参数和文件内容只在 Content-Type 为 {@code application/x-www-form-urlencoded} 或
 *         {@code multipart/form-data} 时可用。</li>
 * </ul>
 */
public class HttpContent {

    private static final Logger log = LoggerFactory.getLogger(HttpContent.class);
    private final FullHttpRequest request;
    private final String contentType;

    private boolean formIsParsed = false;

    /**
     * json格式的请求体
     */
    private String jsonContent;

    /**
     * 表单格式的请求体
     */
    private Map<String, List<String>> formParams;

    /**
     * 文件格式的请求体
     */
    private Map<String, List<FileUpload>> formFiles;

    /**
     * 构造函数，根据给定的 HTTP 请求创建 {@code HttpContent} 实例。
     *
     * @param request HTTP 请求对象，不能为空
     * @throws NullPointerException 当 {@code request} 为 {@code null} 时抛出
     */
    public HttpContent(FullHttpRequest request) {
        Objects.requireNonNull(request, "request can not be null");
        this.request = request;
        this.contentType = HttpRequestUtils.getContentType(request);
    }

    /**
     * 私有构造函数，用于创建空的 {@code HttpContent}。
     */
    private HttpContent() {
        this.request = null;
        this.contentType = null;
    }

    /**
     * 返回请求体的 Content-Type。
     *
     * @return 请求体 Content-Type 字符串
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 获取 JSON 格式的请求体内容。
     *
     * @return JSON 字符串，解析后的内容，如果为空返回 "{}"
     * @throws TurboHttpParseException 当 Content-Type 不是 {@code application/json} 或
     *                                 请求体内容不是有效 JSON 时抛出
     */
    public String getJsonContent() {
        if (!HttpHeaderValues.APPLICATION_JSON.contentEquals(contentType)) {
            throw new TurboHttpParseException("contentType is not application/json");
        }
        // 判断请求体是否被读取
        if (jsonContent != null) {
            return jsonContent;
        }
        ByteBuf contentBuf = request.content();
        // 序列化为json
        String jsonContent = contentBuf.toString(HttpRequestUtils.getRequestCharset(request));
        if (jsonContent == null || jsonContent.isBlank()) {
            jsonContent = "{}";
        } else {
            jsonContent = jsonContent.trim();
        }
        if (jsonContent.startsWith("{") && jsonContent.endsWith("}")) {
            this.jsonContent = jsonContent;
            return jsonContent;
        }
        throw new TurboHttpParseException("The request body is not a valid json");
    }

    /**
     * 获取表单参数。
     *
     * @return 表单参数集合，key 为参数名，value 为值列表
     * @throws TurboHttpParseException 当 Content-Type 不是 {@code application/x-www-form-urlencoded}
     *                                 或 {@code multipart/form-data} 时抛出
     */
    public Map<String, List<String>> getFormParams() {
        if (!HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.contentEquals(contentType)
                && !HttpHeaderValues.MULTIPART_FORM_DATA.contentEquals(contentType)) {
            throw new TurboHttpParseException("contentType is not application/x-www-form-urlencoded or multipart/form-data");
        }
        if (formIsParsed) {
            return formParams;
        }
        parseForm(HttpHeaderValues.MULTIPART_FORM_DATA.contentEquals(contentType));
        return formParams;
    }

    /**
     * 获取 multipart/form-data 文件上传内容。
     *
     * @return 文件上传集合，key 为文件字段名，value 为文件列表
     * @throws TurboHttpParseException 当 Content-Type 不是 {@code multipart/form-data} 时抛出
     */
    public Map<String, List<FileUpload>> getFormFiles() {
        if (!HttpHeaderValues.MULTIPART_FORM_DATA.contentEquals(contentType)) {
            throw new TurboHttpParseException("contentType is not multipart/form-data");
        }
        if (formIsParsed) {
            return formFiles;
        }
        parseForm(true);
        return formFiles;
    }

    /**
     * 解析表单数据，包括普通参数和文件上传。
     *
     * @param isMultiPart 是否为 multipart/form-data
     * @throws TurboHttpParseException 当解析失败时抛出
     */
    private void parseForm(boolean isMultiPart) {
        Map<String, List<String>> formParams = new HashMap<>();
        Map<String, List<FileUpload>> formFiles = new HashMap<>();
        // 创建处理器
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(
                new DefaultHttpDataFactory(Integer.MAX_VALUE),
                request,
                HttpRequestUtils.getRequestCharset(request)
        );
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
                if (isMultiPart) {
                    // 获取文件名
                    String name = fileUpload.getName();
                    if (name == null || name.isBlank()) {
                        log.error("文件上传出现空的name：name:{}", name);
                    }
                    // 存入请求信息中
                    formFiles.computeIfAbsent(name, k -> new ArrayList<>(1)).add(fileUpload);
                } else {
                    fileUpload.release();
                }
            }
        }
        this.formParams = formParams;
        this.formFiles = formFiles;
        formIsParsed = true;
    }


    /**
     * 创建一个空的 {@code HttpContent} 实例。
     *
     * @return 空的 {@code HttpContent} 对象
     */
    public static HttpContent empty() {
        return new HttpContent();
    }


    /**
     * 释放文件上传资源。
     * <p>
     * 调用该方法后，文件上传对象中的资源将被释放。
     */
    public void release() {
        if (formFiles == null || formFiles.isEmpty()) {
            return;
        }
        formFiles.forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                return;
            }
            value.forEach(FileUpload::release);
        });
    }
}
