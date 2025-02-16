package org.turbo.core.router.definition;

import org.turbo.constants.ParameterType;

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
     * 路径参数
     */
    private final List<String> pathParameters = new ArrayList<>(1);

    /**
     * 方法中参数
     */
    private final List<ParameterDefinition> parameterDefinitions = new ArrayList<>(1);

    public RouterMethodDefinition(Class<?> controllerClass) {
        this.controllerClass = controllerClass;
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
     * 添加变量
     *
     * @param parameterDefinition 变量定义
     */
    public void addVariable(ParameterDefinition parameterDefinition) {
        parameterDefinitions.add(parameterDefinition);
    }

    /**
     * 获取变量定义
     *
     * @return 变量定义
     */
    public List<ParameterDefinition> getParameterDefinitions() {
        return parameterDefinitions;
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
}
