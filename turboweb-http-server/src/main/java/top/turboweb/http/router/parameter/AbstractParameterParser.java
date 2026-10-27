package top.turboweb.http.router.parameter;


import java.lang.reflect.Parameter;

/**
 * 抽象的参数解析器，统一入口 parse 直接委派子类实现的 doParse 具体解析
 */
public abstract class AbstractParameterParser implements ParameterParser {
    @Override
    public final ParameterParser.Binder parse(Parameter parameter) {
        return doParse(parameter);
    }

    /**
     * 由子类实现，当前解析器若可以解析该参数，那么该方法会被调用
     *
     * @param parameter 参数信息
     * @return 对参数进行数据绑定的绑定器
     */
    protected abstract ParameterParser.Binder doParse(Parameter parameter);
}
