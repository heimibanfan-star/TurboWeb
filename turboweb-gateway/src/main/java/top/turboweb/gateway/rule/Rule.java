package top.turboweb.gateway.rule;

/**
 * 映射规则
 */
public interface Rule {


    /**
     * 获取服务名
     * @param path 请求路径
     * @return 服务名
     */
    String getServiceName(String path);

    /**
     * 是否被使用
     * @return 是否被使用
     */
    boolean isUsed();
}
