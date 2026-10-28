package top.turboweb.server.runtime;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 资源管理器：统一持有装配期登记的可关闭资源，供按名字或按类型查找，
 * 生命周期结束时由 {@link #free()} 按登记顺序统一释放。
 * <p>
 * 资源经 {@link Builder#addResource(AutoCloseable)} 或
 * {@link Builder#addResource(String, AutoCloseable)} 登记进建造者，
 * 每个资源必须有名字（未显式指定时默认取资源对象类名的小驼峰形式），
 * 建造期保证资源名不重复、同一实例不会被登记两次。
 * <p>
 * 查找口径：带名字的查找按名字精确定位；只带类型的查找遍历全部资源，
 * 命中零个或多个都抛出 {@link ResourceException}。
 * 本实例装配完成后不再持有可变状态，可被多线程并发读取。
 *
 * <pre>{@code
 * ResourceManager manager = new ResourceManager.Builder()
 *         .addResource(sessionManager)
 *         .addResource("dbPool", dataSource)
 *         .build();
 * }</pre>
 */
public class ResourceManager {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ResourceManager.class);

    /**
     * 已登记的资源，按资源名索引；LinkedHashMap保持登记顺序，使释放顺序与登记顺序一致、可预期
     */
    private final Map<String, AutoCloseable> resourcesWithName;
    /**
     * 是否已释放：保证每个资源至多被关闭一次
     */
    private final AtomicBoolean freed = new AtomicBoolean(false);

    private ResourceManager(Map<String, AutoCloseable> resourcesWithName) {
        this.resourcesWithName = resourcesWithName;
    }

    /**
     * 按名字获取资源并校验类型：名字精确定位唯一资源，
     * 资源不存在或不是期望类型时抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @param type         资源类型
     * @param <T>          资源的类型
     * @return 资源
     */
    public <T extends AutoCloseable> T getResource(String resourceName, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        AutoCloseable resource = getResource(resourceName);
        if (!type.isInstance(resource)) {
            throw new ResourceException("The type of resource " + resourceName + " does not match the expected type: " + resource.getClass().getName());
        }
        return type.cast(resource);
    }

    /**
     * 按名字获取资源：名字精确定位，资源不存在抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @return 资源
     */
    public AutoCloseable getResource(String resourceName) {
        AutoCloseable resource = resourcesWithName.get(resourceName);
        if (resource == null) {
            throw new ResourceException("Resource not found: " + resourceName);
        }
        return resource;
    }

    /**
     * 按类型获取资源：遍历全部资源找出所有匹配的，命中一个返回之，
     * 命中零个或多个抛出 {@link ResourceException}
     *
     * @param type 资源类型
     * @param <T>  资源的类型
     * @return 资源
     */
    public <T extends AutoCloseable> T getResource(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        AutoCloseable matched = null;
        for (AutoCloseable resource : resourcesWithName.values()) {
            if (type.isInstance(resource)) {
                if (matched != null) {
                    throw new ResourceException("Multiple resources found for type " + type.getName() + ": "
                            + matched.getClass().getName() + ", " + resource.getClass().getName());
                }
                matched = resource;
            }
        }
        if (matched == null) {
            throw new ResourceException("No resource found for type " + type.getName());
        }
        return type.cast(matched);
    }

    /**
     * 释放全部已登记的资源：按登记顺序依次调用各资源的close，单个资源的释放异常只记录日志，
     * 不中断其余释放；重复调用为空操作（幂等），释放后资源处于已关闭状态
     */
    public void free() {
        // CAS保证并发调用下每个资源至多被关闭一次
        if (!freed.compareAndSet(false, true)) {
            return;
        }
        for (AutoCloseable resource : resourcesWithName.values()) {
            try {
                resource.close();
            } catch (Exception err) {
                log.error("Failed to close resource", err);
            }
        }
    }

    /**
     * {@link ResourceManager} 的建造者：链式登记资源，链式方法校验非法参数立即抛出
     * {@link IllegalArgumentException}。
     * <p>
     * 建造期保证登记约束：资源名不重复（未显式指定名字的资源默认取类名的小驼峰形式，
     * 如 {@code MemorySessionManager} 的默认名为 {@code memorySessionManager}），
     * 同一实例（按对象同一性判断）不会被登记两次。
     */
    public static class Builder {

        /**
         * 已登记的资源，按资源名索引；LinkedHashMap保持登记顺序
         */
        private final Map<String, AutoCloseable> resourcesWithName = new LinkedHashMap<>();
        /**
         * 已登记的资源实例，按对象同一性判重
         */
        private final Set<AutoCloseable> resources = Collections.newSetFromMap(new IdentityHashMap<>());

        /**
         * 登记资源并显式指定名字：名字不能为空，资源不能为null，名字或实例重复立即抛出
         * {@link IllegalArgumentException}
         */
        public Builder addResource(String resourceName, AutoCloseable resource) {
            if (resourceName == null || resourceName.isBlank()) {
                throw new IllegalArgumentException("resourceName can not be blank");
            }
            Objects.requireNonNull(resource, "resource must not be null");
            checkNotDuplicated(resourceName, resource);
            resourcesWithName.put(resourceName, resource);
            resources.add(resource);
            return this;
        }

        /**
         * 登记资源，名字默认取资源对象类名的小驼峰形式（如 {@code MemorySessionManager}
         * 的默认名为 {@code memorySessionManager}）；匿名类无法推导默认名，须改用
         * {@link #addResource(String, AutoCloseable)} 显式指定
         */
        public Builder addResource(AutoCloseable resource) {
            Objects.requireNonNull(resource, "resource must not be null");
            return addResource(defaultName(resource.getClass()), resource);
        }

        /**
         * 构建资源管理器：登记的约束已在各addResource链式方法即时校验，
         * 装配后的实例为只读视图，可被多线程并发读取
         */
        public ResourceManager build() {
            return new ResourceManager(Collections.unmodifiableMap(new LinkedHashMap<>(resourcesWithName)));
        }

        /**
         * 建造期登记约束：资源名不重复、同一实例不重复登记，违反立即抛出 {@link IllegalArgumentException}
         */
        private void checkNotDuplicated(String resourceName, AutoCloseable resource) {
            if (resourcesWithName.containsKey(resourceName)) {
                throw new IllegalArgumentException("Duplicate resource name: " + resourceName);
            }
            if (resources.contains(resource)) {
                throw new IllegalArgumentException("Duplicate resource: " + resource.getClass());
            }
        }

        /**
         * 推导资源的默认名字：取类名的小驼峰形式；类名以两个大写字母开头时（如URL）保持原样不转小写
         */
        private String defaultName(Class<?> type) {
            String name = type.getSimpleName();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Cannot derive a default resource name for an anonymous class, please specify the name explicitly");
            }
            if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
                return name;
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
    }
}
