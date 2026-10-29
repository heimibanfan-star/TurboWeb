package top.turboweb.server.runtime;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 资源管理器：分三个空间持有装配期登记的可关闭资源，生命周期结束时由 {@link #free()}
 * 按全局登记顺序统一释放。
 * <p>
 * 三个空间的登记口径与可见性各不相同：
 * <ul>
 *     <li><b>内置空间</b>：框架装配期持有的资源，经 {@link Builder#build(Map)} 一次性传入，
 *     键为资源名；经 {@link #getBuiltinResource(String)} 或
 *     {@link #getBuiltinResource(String, Class)} 按名查找，自定义空间的查询不可见；</li>
 *     <li><b>自定义空间</b>：使用方业务资源，经 {@link Builder#addResource(String, AutoCloseable)}
 *     或 {@link Builder#addResource(AutoCloseable)} 链式登记；经 {@link #getResource(String)}、
 *     {@link #getResource(String, Class)} 与 {@link #getResource(Class)} 查找，
 *     内置空间的资源对本空间全部查找不可见；两空间的命名互不影响，同名可各存一份；</li>
 *     <li><b>托管空间</b>：仅为关闭而生、无人查找的资源，经 {@link Builder#addManaged(AutoCloseable)}
 *     登记且无名字，任何查找接口都不可见，只在 {@link #free()} 时被释放。</li>
 * </ul>
 * 三空间之间按对象同一性判重：同一实例无论落在哪个空间都只能登记一次，
 * 杜绝同一资源被查找方与释放方重复处置。
 * <p>
 * 释放顺序为全局登记顺序：链式登记的资源（自定义与托管按登记先后）在前，
 * build 传入的内置资源按传入 Map 的迭代序在后；单个资源的释放异常只记录日志，
 * 不中断其余释放，重复调用为空操作（幂等）。
 * 本实例构建完成后不再持有可变状态，可被多线程并发读取。
 *
 * <pre>{@code
 * Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
 * builtins.put("microTaskExecutor", executor);
 * ResourceManager manager = new ResourceManager.Builder()
 *         .addResource(sessionManager)
 *         .addManaged(destroyer)
 *         .build(builtins);
 * }</pre>
 */
public class ResourceManager {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ResourceManager.class);

    /**
     * 内置空间资源，按资源名索引；LinkedHashMap保持传入顺序，使释放顺序与传入顺序一致、可预期
     */
    private final Map<String, AutoCloseable> builtinResources;
    /**
     * 自定义空间资源，按资源名索引；LinkedHashMap保持登记顺序
     */
    private final Map<String, AutoCloseable> customResources;
    /**
     * 全局释放顺序：链式登记的资源在前，build传入的内置资源按迭代序在后；
     * 托管资源不参与任何查询，在本表中的成员即其空间全体
     */
    private final List<AutoCloseable> freeOrder;
    /**
     * 是否已释放：保证每个资源至多被关闭一次
     */
    private final AtomicBoolean freed = new AtomicBoolean(false);

    private ResourceManager(Map<String, AutoCloseable> builtinResources,
                            Map<String, AutoCloseable> customResources,
                            List<AutoCloseable> freeOrder) {
        this.builtinResources = builtinResources;
        this.customResources = customResources;
        this.freeOrder = freeOrder;
    }

    /**
     * 按名字获取内置资源并校验类型：名字精确定位唯一资源，
     * 资源不存在或不是期望类型时抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @param type         资源类型
     * @param <T>          资源的类型
     * @return 资源
     */
    public <T extends AutoCloseable> T getBuiltinResource(String resourceName, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return checkedCast(resourceName, getBuiltinResource(resourceName), type);
    }

    /**
     * 按名字获取内置资源：名字精确定位，资源不存在抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @return 资源
     */
    public AutoCloseable getBuiltinResource(String resourceName) {
        AutoCloseable resource = builtinResources.get(resourceName);
        if (resource == null) {
            throw new ResourceException("Resource not found: " + resourceName);
        }
        return resource;
    }

    /**
     * 按名字获取自定义资源并校验类型：名字精确定位唯一资源，
     * 资源不存在或不是期望类型时抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @param type         资源类型
     * @param <T>          资源的类型
     * @return 资源
     */
    public <T extends AutoCloseable> T getResource(String resourceName, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return checkedCast(resourceName, getResource(resourceName), type);
    }

    /**
     * 类型校验并窄化：资源不是期望类型时抛出 {@link ResourceException}，消息指名资源与期望类型
     */
    private <T extends AutoCloseable> T checkedCast(String resourceName, AutoCloseable resource, Class<T> type) {
        if (!type.isInstance(resource)) {
            throw new ResourceException("The type of resource " + resourceName + " does not match the expected type: " + resource.getClass().getName());
        }
        return type.cast(resource);
    }

    /**
     * 按名字获取自定义资源：名字精确定位，资源不存在抛出 {@link ResourceException}
     *
     * @param resourceName 资源名称
     * @return 资源
     */
    public AutoCloseable getResource(String resourceName) {
        AutoCloseable resource = customResources.get(resourceName);
        if (resource == null) {
            throw new ResourceException("Resource not found: " + resourceName);
        }
        return resource;
    }

    /**
     * 按类型获取自定义资源：遍历自定义空间找出所有匹配的，命中一个返回之，
     * 命中零个或多个抛出 {@link ResourceException}
     *
     * @param type 资源类型
     * @param <T>  资源的类型
     * @return 资源
     */
    public <T extends AutoCloseable> T getResource(Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        AutoCloseable matched = null;
        for (AutoCloseable resource : customResources.values()) {
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
     * 释放全部已登记的资源：三空间按全局登记顺序（链式登记在前、内置按传入序在后）
     * 依次调用各资源的close，单个资源的释放异常只记录日志，不中断其余释放；
     * 重复调用为空操作（幂等），释放后资源处于已关闭状态
     */
    public void free() {
        // CAS保证并发调用下每个资源至多被关闭一次
        if (!freed.compareAndSet(false, true)) {
            return;
        }
        for (AutoCloseable resource : freeOrder) {
            try {
                resource.close();
            } catch (Exception err) {
                log.error("Failed to close resource", err);
            }
        }
    }

    /**
     * {@link ResourceManager} 的建造者：自定义与托管资源链式登记，内置资源在
     * {@link #build(Map)} 一次性传入；链式方法校验非法参数立即抛出 {@link IllegalArgumentException}。
     * <p>
     * 建造期保证登记约束：同一空间内资源名不重复（自定义资源未显式指定名字时默认取类名的
     * 小驼峰形式，如 {@code MemorySessionManager} 的默认名为 {@code memorySessionManager}），
     * 三空间之间同一实例（按对象同一性判断）不会被登记两次；
     * 内置资源的校验（名字非空、资源非null、实例判重）集中在 {@link #build(Map)} 执行。
     */
    public static class Builder {

        /**
         * 链式登记的自定义空间资源，按资源名索引；LinkedHashMap保持登记顺序
         */
        private final Map<String, AutoCloseable> customResources = new LinkedHashMap<>();
        /**
         * 已登记的资源实例，按对象同一性判重：覆盖自定义与托管两个链式登记的空间
         */
        private final Set<AutoCloseable> registered = Collections.newSetFromMap(new IdentityHashMap<>());
        /**
         * 链式登记顺序：自定义与托管按登记先后混排，构成释放顺序的链上前段
         */
        private final List<AutoCloseable> chainOrder = new ArrayList<>();

        /**
         * 登记自定义资源并显式指定名字：名字不能为空，资源不能为null，名字或实例重复立即抛出
         * {@link IllegalArgumentException}
         */
        public Builder addResource(String resourceName, AutoCloseable resource) {
            if (resourceName == null || resourceName.isBlank()) {
                throw new IllegalArgumentException("resourceName can not be blank");
            }
            Objects.requireNonNull(resource, "resource must not be null");
            checkNotDuplicated(resourceName, resource);
            customResources.put(resourceName, resource);
            chainOrder.add(resource);
            return this;
        }

        /**
         * 登记自定义资源，名字默认取资源对象类名的小驼峰形式（如 {@code MemorySessionManager}
         * 的默认名为 {@code memorySessionManager}）；匿名类无法推导默认名，须改用
         * {@link #addResource(String, AutoCloseable)} 显式指定
         */
        public Builder addResource(AutoCloseable resource) {
            Objects.requireNonNull(resource, "resource must not be null");
            return addResource(defaultName(resource.getClass()), resource);
        }

        /**
         * 登记托管资源：无名字、不可被任何查找接口获取，只在统一释放时被关闭；
         * 资源不能为null，同一实例重复登记立即抛出 {@link IllegalArgumentException}
         */
        public Builder addManaged(AutoCloseable resource) {
            Objects.requireNonNull(resource, "resource must not be null");
            checkNotDuplicated(null, resource);
            chainOrder.add(resource);
            return this;
        }

        /**
         * 构建资源管理器，内置空间为空：自定义与托管的登记约束已在各链式方法即时校验，
         * 装配后的实例为只读视图，可被多线程并发读取
         */
        public ResourceManager build() {
            return build(Collections.emptyMap());
        }

        /**
         * 构建资源管理器并一次性传入内置空间：键为资源名（不能为空），值为资源（不能为null），
         * Map的迭代序即内置资源的登记与释放顺序；内置资源与链上已登记实例重复、
         * 或Map内同一实例出现在多个键下，抛出 {@link IllegalArgumentException}。
         * 构建后三空间均为只读视图，传入的Map后续变化不影响构建产物
         *
         * @param builtinResources 内置资源表，不能为null
         * @return 以当前收集值装配的新资源管理器
         * @throws NullPointerException builtinResources为null或内置资源为null
         * @throws IllegalArgumentException 内置资源名为空，或内置资源实例与链上登记重复
         */
        public ResourceManager build(Map<String, AutoCloseable> builtinResources) {
            Objects.requireNonNull(builtinResources, "builtinResources must not be null");
            Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
            Set<AutoCloseable> builtinInstances = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Map.Entry<String, AutoCloseable> entry : builtinResources.entrySet()) {
                String resourceName = entry.getKey();
                AutoCloseable resource = entry.getValue();
                if (resourceName == null || resourceName.isBlank()) {
                    throw new IllegalArgumentException("resourceName can not be blank");
                }
                Objects.requireNonNull(resource, "resource must not be null");
                if (!registered.add(resource) || !builtinInstances.add(resource)) {
                    throw new IllegalArgumentException("Duplicate resource: " + resource.getClass());
                }
                builtins.put(resourceName, resource);
            }
            List<AutoCloseable> freeOrder = new ArrayList<>(chainOrder);
            freeOrder.addAll(builtins.values());
            return new ResourceManager(
                    Collections.unmodifiableMap(builtins),
                    Collections.unmodifiableMap(new LinkedHashMap<>(customResources)),
                    Collections.unmodifiableList(freeOrder));
        }

        /**
         * 建造期登记约束：资源名不重复、同一实例不重复登记，违反立即抛出 {@link IllegalArgumentException}；
         * 托管资源无名字，名字判重仅对自定义资源生效
         */
        private void checkNotDuplicated(String resourceName, AutoCloseable resource) {
            if (resourceName != null && customResources.containsKey(resourceName)) {
                throw new IllegalArgumentException("Duplicate resource name: " + resourceName);
            }
            if (!registered.add(resource)) {
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
