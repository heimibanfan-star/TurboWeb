package top.turboweb.client.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.commons.utils.base.BeanUtils;

/**
 * json格式请求体的请求构造器
 */
public class JsonBodyBuilder extends HttpBaseBuilder {

    /**
     * 请求体
     */
    private Object content;

    public JsonBodyBuilder(String url, HttpMethod method) {
        super(method, url);
    }

    /**
     * 设置json的请求体
     * @param content 需要序列化的对象
     */
    public void setJsonContent(Object content) {
        this.content = content;
    }

    /**
     * 获取转化为json的请求体内容，如果请求体为null，返回null
     * @return json字符串
     */
    public String getJsonContent() {
        if (content == null) {
            return null;
        }
        try {
            return BeanUtils.getObjectMapper().writeValueAsString(this.content);
        } catch (JsonProcessingException ignore) {
        }
        return null;
    }
}
