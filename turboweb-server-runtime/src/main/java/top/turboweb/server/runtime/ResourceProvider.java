package top.turboweb.server.runtime;

/**
 * 资源提供者：装配期登记资源的只读查询视图，按空间暴露内置与自定义资源的查找能力。
 * <p>
 * 本接口只收编查询方法，不含任何释放操作：持有本接口的调用方（如响应写出器）可按需取用资源，
 * 但无法触发统一释放；释放生命周期由 {@link ResourceManager} 独自持有，
 * 防止使用方在服务期内非法释放资源。
 * <p>
 * 空间可见性与 {@link ResourceManager} 一致：内置空间资源仅对
 * {@link #getBuiltinResource(String)} 系查找可见，自定义空间资源仅对
 * {@link #getResource(String)} 系与按类型查找可见，两空间命名互不影响。
 */
public interface ResourceProvider {

    /**
     * 按名字获取内置资源并校验类型：名字精确定位唯一资源，
     * 资源不存在或不是期望类型时抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @param type         资源类型
     * @param <T>          资源的类型
     * @return 资源
     */
    <T extends AutoCloseable> T getBuiltinResource(String resourceName, Class<T> type);

    /**
     * 按名字获取内置资源：名字精确定位，资源不存在抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @return 资源
     */
    AutoCloseable getBuiltinResource(String resourceName);

    /**
     * 按名字获取自定义资源并校验类型：名字精确定位唯一资源，
     * 资源不存在或不是期望类型时抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @param type         资源类型
     * @param <T>          资源的类型
     * @return 资源
     */
    <T extends AutoCloseable> T getResource(String resourceName, Class<T> type);

    /**
     * 按名字获取自定义资源：名字精确定位，资源不存在抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @return 资源
     */
    AutoCloseable getResource(String resourceName);

    /**
     * 按类型获取自定义资源：遍历自定义空间找出所有匹配的，命中一个返回之，
     * 命中零个或多个抛出 {@link ResourceException}
     *
     * @param type 资源类型
     * @param <T>  资源的类型
     * @return 资源
     */
    <T extends AutoCloseable> T getResource(Class<T> type);
}
