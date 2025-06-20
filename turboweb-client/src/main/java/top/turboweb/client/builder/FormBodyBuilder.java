package top.turboweb.client.builder;

import io.netty.handler.codec.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多部份表单请求体类型的请求构造器
 */
public class FormBodyBuilder extends HttpBaseBuilder {

    /**
     * 表单参数
     */
    private final List<ParamEntity<String>> formParams = new ArrayList<>();

    public FormBodyBuilder(HttpMethod method, String url) {
        super(method, url);
    }

    /**
     * 添加表单参数
     * @param key 参数的键
     * @param value 参数的值
     */
    public void addFormParam(String key, Object value) {
        ParamEntity<String> entity = new ParamEntity<>(key, value.toString());
        formParams.add(entity);
    }

    /**
     * 添加多个表单参数
     * @param params 存储表单参数的map集合
     */
    public void addFormParams(Map<String, Object> params) {
        for (Map.Entry<String, Object> mapEntry : params.entrySet()) {
            ParamEntity<String> entity = new ParamEntity<>(mapEntry.getKey(), mapEntry.getValue().toString());
            formParams.add(entity);
        }
    }

    /**
     * 获取表单参数
     * @return 表单参数
     */
    public List<ParamEntity<String>> getFormParams() {
        return this.formParams;
    }
}
