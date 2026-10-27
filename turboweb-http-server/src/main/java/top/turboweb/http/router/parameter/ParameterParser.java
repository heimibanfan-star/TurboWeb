package top.turboweb.http.router.parameter;

import top.turboweb.http.context.HttpContext;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

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
         * @param requestParameter 路由装配就位的非请求体参数承载(路径参数与查询参数,均已按缺省字符集解码);不取参数的绑定器可忽略
         * @param beanValidator Bean 校验器，绑定结果为 bean 且需要校验时由绑定器经其执行校验
         * @param jsonSerializer json 序列化器，绑定结果为 bean 或取自 JSON 体字符串时由绑定器经其完成转化;不经 bean 通道的绑定器可忽略
         * @return 参数
         */
        Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                             JsonSerializer jsonSerializer);
    }
}
