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
 * http请求体的内容
 */
public class HttpContent {

    private static final Logger log = LoggerFactory.getLogger(HttpContent.class);
    private final FullHttpRequest request;
    private final String contentType;

    public HttpContent(FullHttpRequest request) {
        Objects.requireNonNull(request, "request can not be null");
        this.request = request;
        this.contentType = HttpRequestUtils.getContentType(request);
    }

    private HttpContent() {
        this.request = null;
        this.contentType = null;
    }

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

    public String getContentType() {
        return contentType;
    }

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
     * 表单格式的请求体
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
     * 文件格式的请求体
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
     * 解析表单数据
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


    public static HttpContent empty() {
        return new HttpContent();
    }


    /**
     * 释放资源
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
