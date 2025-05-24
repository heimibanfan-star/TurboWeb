package org.turboweb.http.router.definition;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 路由方法的定义信息
 */
public class RouterMethodDefinition {

    /**
     * 控制器的类
     */
    private final Class<?> controllerClass;

    /**
     * 路径变量的数量
     */
    private int pathVariableCount = 0;

    /**
     * 路径正则表达式
     */
    private Pattern pattern;

    /**
     * 方法
     */
    private final MethodHandle methodHandle;

    /**
     * 路径参数
     */
    private final List<String> pathParameters = new ArrayList<>(1);


    public RouterMethodDefinition(Class<?> controllerClass, MethodHandle methodHandle) {
        this.controllerClass = controllerClass;
        this.methodHandle = methodHandle;
    }

    /**
     * 获取控制器的类
     *
     * @return 控制器的类
     */
    public Class<?> getControllerClass() {
        return controllerClass;
    }

    public int getPathVariableCount() {
        return pathVariableCount;
    }

    public void setPathVariableCount(int pathVariableCount) {
        this.pathVariableCount = pathVariableCount;
    }

    /**
     * 获取路径参数
     *
     * @return 路径参数
     */
    public List<String> getPathParameters() {
        return pathParameters;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public MethodHandle getMethod() {
        return methodHandle;
    }
}
