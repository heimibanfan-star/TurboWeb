package top.turboweb.http.router.parameter;

import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * 参数解析器
 */
public interface ParameterParser {

    /**
     * 解析参数信息
     * @param parameter 参数
     * @return 参数绑定信息
     */
    Binder parse(Parameter parameter);

    /**
     * 自动注入的参数绑定器
     */
    interface Binder {

        /**
         * 解析参数
         * @param ctx 请求上下文
         * @return 参数
         */
        Object bindParameter(HttpContext ctx);
    }
}
