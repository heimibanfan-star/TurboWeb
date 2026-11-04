package top.turboweb.client;

import top.turboweb.http.core.HttpAddress;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 远程 http 端口的工厂：持有传输引擎与命名空间映射两个定型属性，
 * 建造期一次赋值，构建后不可再修改。
 *
 * <p>命名空间映射为命名空间名到地址数组的条目集合；映射与各数组在建造时
 * 防御性拷贝，读取时同样返回隔离副本，外部任何修改都不影响工厂持有的内容。</p>
 */
public final class RemoteHttpPortFactory {

    private final HttpClientEngine engine;
    private final Map<String, HttpAddress[]> namespaces;

    /**
     * 按建造者收集的属性值建立工厂，映射与数组在此防御性拷贝。
     *
     * @param builder 已收集完属性值的建造者
     */
    private RemoteHttpPortFactory(Builder builder) {
        this.engine = builder.engine;
        this.namespaces = deepCopy(builder.namespaces);
    }

    /**
     * 创建建造者，两属性从缺位状态起步。
     *
     * @return 新建造者
     */
    public static Builder builder() {
        return new Builder();
    }

    /** 传输引擎，建造期定型。 */
    public HttpClientEngine engine() {
        return engine;
    }

    /**
     * 命名空间映射：键为命名空间名，值为该命名空间的地址数组。
     * 返回不可修改视图，且各数组为隔离副本，修改返回值不影响工厂。
     *
     * @return 不可修改的命名空间映射
     */
    public Map<String, HttpAddress[]> namespaces() {
        return Collections.unmodifiableMap(deepCopy(namespaces));
    }

    /**
     * 深拷贝命名空间映射：条目按原顺序转入新映射，各值数组逐个克隆。
     */
    private static Map<String, HttpAddress[]> deepCopy(Map<String, HttpAddress[]> source) {
        Map<String, HttpAddress[]> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, values.clone()));
        return copy;
    }

    /**
     * {@link RemoteHttpPortFactory} 的建造者：引擎与命名空间映射各一个配置口，
     * 同名命名空间后写覆盖先写，构建时转入不可变工厂。
     */
    public static final class Builder {

        private HttpClientEngine engine;
        private final Map<String, HttpAddress[]> namespaces = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 设置传输引擎，必须提供。
         *
         * @param engine 传输引擎，不能为 {@code null}
         * @return 本建造者
         * @throws NullPointerException 当 {@code engine} 为 {@code null} 时抛出
         */
        public Builder engine(HttpClientEngine engine) {
            this.engine = Objects.requireNonNull(engine, "engine must not be null");
            return this;
        }

        /**
         * 追加一个命名空间条目。
         *
         * @param name      命名空间名，不能为 {@code null}
         * @param addresses 该命名空间的地址数组，不能为 {@code null}，元素也不能为 {@code null}
         * @return 本建造者
         * @throws NullPointerException 当 {@code name}、{@code addresses} 或任一元素为 {@code null} 时抛出
         */
        public Builder namespace(String name, HttpAddress... addresses) {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(addresses, "addresses must not be null");
            for (HttpAddress address : addresses) {
                Objects.requireNonNull(address, "namespace address must not be null");
            }
            this.namespaces.put(name, addresses.clone());
            return this;
        }

        /**
         * 批量并入命名空间条目，同名命名空间覆盖既有条目。
         *
         * @param source 命名空间映射，不能为 {@code null}，条目值不能为 {@code null}
         * @return 本建造者
         * @throws NullPointerException 当 {@code source} 或任一条目、元素为 {@code null} 时抛出
         */
        public Builder namespaces(Map<String, HttpAddress[]> source) {
            Objects.requireNonNull(source, "namespaces must not be null");
            source.forEach(this::namespace);
            return this;
        }

        /**
         * 构建不可变的工厂对象。
         *
         * @return 远程 http 端口工厂
         * @throws NullPointerException 当引擎未设置时抛出
         */
        public RemoteHttpPortFactory build() {
            Objects.requireNonNull(engine, "engine must not be null");
            return new RemoteHttpPortFactory(this);
        }
    }
}
