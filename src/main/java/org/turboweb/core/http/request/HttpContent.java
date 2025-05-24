package org.turboweb.core.http.request;

import io.netty.handler.codec.http.multipart.FileUpload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * http请求体的内容
 */
public class HttpContent {

    /**
     * 请求格式
     */
    private String contentType;

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

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getJsonContent() {
        return jsonContent;
    }

    public void setJsonContent(String jsonContent) {
        this.jsonContent = jsonContent;
    }

    public Map<String, List<String>> getFormParams() {
        return formParams;
    }

    public void setFormParams(Map<String, List<String>> formParams) {
        this.formParams = formParams;
    }

    public Map<String, List<FileUpload>> getFormFiles() {
        return formFiles;
    }

    public void setFormFiles(Map<String, List<FileUpload>> formFiles) {
        this.formFiles = formFiles;
    }

    public static HttpContent empty() {
        HttpContent content = new HttpContent();
        content.setFormParams(new HashMap<>(0));
        content.setFormFiles(new HashMap<>(0));
        return content;
    }
}
