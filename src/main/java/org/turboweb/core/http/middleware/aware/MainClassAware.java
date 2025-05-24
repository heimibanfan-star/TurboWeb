package org.turboweb.core.http.middleware.aware;

/**
 * 注入主启动类的接口
 */
public interface MainClassAware {

    /**
     * 注入主启动类
     * @param mainClass 主启动类
     */
    void setMainClass(Class<?> mainClass);
}
