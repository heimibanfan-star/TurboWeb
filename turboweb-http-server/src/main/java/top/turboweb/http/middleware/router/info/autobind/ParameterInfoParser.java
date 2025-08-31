package top.turboweb.http.middleware.router.info.autobind;

import java.lang.reflect.Parameter;

/**
 * 参数信息解析器
 */
public interface ParameterInfoParser {

    /**
     * 解析参数信息
     * @param parameter 参数
     * @return 参数绑定信息
     */
    ParameterBinder parse(Parameter parameter);
}
