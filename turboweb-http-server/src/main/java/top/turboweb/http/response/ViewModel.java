package top.turboweb.http.response;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于渲染视图模板的模型
 */
public class ViewModel {
    // 存储数据的属性
    private Map<String, Object> attributes = new HashMap<>();
    // 视图文件的名字
    private String viewName;

    /**
     * 设置视图名称
     *
     * @param viewName 视图名称
     */
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    /**
     * 获取视图名称
     *
     * @return 视图名称
     */
    public String getViewName() {
        return viewName;
    }

    /**
     * 在原有属性中添加新的属性
     *
     * @param name 属性名称
     * @param value 属性的值
     */
    public void addAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    /**
     * 获取属性的值
     *
     * @param name 属性名称
     * @return 属性的值
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * 设置属性，直接用新属性替换之前的属性
     *
     * @param attributes 属性
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * 获取属性
     *
     * @return 属性
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
