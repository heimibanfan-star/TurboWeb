package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.anno.param.parser.Parser;

import java.lang.reflect.Parameter;

/**
 * 抽象的参数解析器，对参数的一些和绑定参数无关的注解进行解析
 */
public abstract class AbstractParamInfoParser implements ParameterInfoParser {
    @Override
    public final ParameterBinder parse(Parameter parameter) {
        // 判断改参数指定自定义解析器
        if (parameter.isAnnotationPresent(Parser.class)) {
            // 获取解析器的注解
            Parser parser = parameter.getAnnotation(Parser.class);
            Class<?> parserClass = parser.value();
            // 如果当前解析器不可以处理，直接返回null，交给后续解析器解析
            if (parserClass != this.getClass()) {
                return null;
            }
        }
        // 如果没有被特定注解标注或当前解析器可以解析，直接在本解析器中尝试解析
        return doParse(parameter);
    }

    /**
     * 由子类实现，当前解析器若可以解析该参数，那么该方法会被调用
     *
     * @param parameter 参数信息
     * @return 对参数进行数据绑定的绑定器
     */
    protected abstract ParameterBinder doParse(Parameter parameter);
}
