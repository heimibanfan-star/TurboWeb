package top.turboweb.http.router.parameter;

import top.turboweb.http.context.HttpContext;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.util.Map;

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
         * @param pathParams 路由匹配携带的路径参数映射(已按缺省字符集解码),键为参数名;不用路径参数的绑定器可忽略
         * @param beanValidator Bean 校验器，绑定结果为 bean 且需要校验时由绑定器经其执行校验
         * @return 参数
         */
        Object bindParameter(HttpContext ctx, Map<String, String> pathParams, BeanValidator beanValidator);
    }
}
