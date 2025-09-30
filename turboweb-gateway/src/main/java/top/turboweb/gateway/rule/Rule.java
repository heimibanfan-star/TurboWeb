package top.turboweb.gateway.rule;

/**
 * 映射规则
 */
public interface Rule {


    /**
     * 获取本地服务
     * @param path 路径
     * @return 本地服务
     */
    RuleDetail getLocalService(String path);

    /**
     * 获取远程服务
     * @param path 路径
     * @return 远程服务
     */
    RuleDetail getRemoteService(String path);

    /**
     * 获取服务
     * @param path 路径
     * @return 服务
     */
    RuleDetail getService(String path);

    /**
     * 是否被使用
     * @return 是否被使用
     */
    boolean isUsed();
}
