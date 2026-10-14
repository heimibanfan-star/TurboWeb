package top.turboweb.http.orchestrator;

import java.util.List;
import java.util.Objects;

/**
 * 跨域（CORS）不可变配置：集中持有允许的来源、方法、请求头、可暴露响应头、
 * 是否允许携带凭证与预检缓存时长。
 * <p>
 * 经 {@link #builder()} 装配、{@link Builder#build()} 一次性产出：字段全部 final、
 * 无修改入口，列表属性以不可变列表防御拷贝——构建之后外部列表再变更不影响产物。
 * </p>
 * <p>
 * 通配语义：来源、方法、请求头列表中的通配项 * 匹配任意对应值，
 * 由 {@link #isAnyOrigin()} 等判定与 {@link #isAllowedOrigin(String)} 匹配提供，
 * 供 CORS 响应环节消费；响应头的写出形态由消费方决定，本类只存值。
 * </p>
 */
public class HttpCorsConfig {

    /** 通配项：匹配任意对应值。 */
    private static final String WILDCARD = "*";

    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;
    private final List<String> exposedHeaders;
    private final boolean allowCredentials;
    private final int maxAge;

    /**
     * 由建造者装配产物，配置对象对外不提供公有构造入口。
     *
     * @param builder 已收集完属性值的建造者
     */
    private HttpCorsConfig(Builder builder) {
        this.allowedOrigins = builder.allowedOrigins;
        this.allowedMethods = builder.allowedMethods;
        this.allowedHeaders = builder.allowedHeaders;
        this.exposedHeaders = builder.exposedHeaders;
        this.allowCredentials = builder.allowCredentials;
        this.maxAge = builder.maxAge;
    }

    /**
     * 创建建造者，各属性从默认值起步。
     *
     * @return 新建造者
     */
    public static Builder builder() {
        return new Builder();
    }

    /** 允许的跨域来源列表（不可变）。 */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /** 允许的跨域请求方法列表（不可变）。 */
    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    /** 允许的请求头列表（不可变）。 */
    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    /** 可暴露给浏览器的响应头列表（不可变）。 */
    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    /** 是否允许携带凭证。 */
    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    /** 预检请求缓存时间（秒）。 */
    public int getMaxAge() {
        return maxAge;
    }

    /** 来源列表是否含通配项（默认配置即为通配）。 */
    public boolean isAnyOrigin() {
        return allowedOrigins.contains(WILDCARD);
    }

    /** 方法列表是否含通配项。 */
    public boolean isAnyMethod() {
        return allowedMethods.contains(WILDCARD);
    }

    /** 请求头列表是否含通配项。 */
    public boolean isAnyHeader() {
        return allowedHeaders.contains(WILDCARD);
    }

    /**
     * 判定请求来源是否允许：通配项放行任意非空来源，否则要求与来源列表精确相等。
     *
     * @param origin 请求头 Origin 的值，可以为 null
     * @return 是否允许；origin 为 null 时返回 false
     */
    public boolean isAllowedOrigin(String origin) {
        return origin != null && (isAnyOrigin() || allowedOrigins.contains(origin));
    }

    /**
     * {@link HttpCorsConfig} 的建造者：每个属性一个流式配置方法，
     * 重复配置以最后一次为准，未配置者取默认值；
     * 列表属性在配置时即转不可变拷贝（同时拒收 null 列表与 null 元素），
     * 与调用方传入的列表隔离。{@link #build()} 可多次调用，产物互不影响。
     */
    public static class Builder {

        private List<String> allowedOrigins = List.of(WILDCARD);
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE");
        private List<String> allowedHeaders = List.of(WILDCARD);
        private List<String> exposedHeaders = List.of("Content-Disposition");
        private boolean allowCredentials = false;
        private int maxAge = 3600;

        /** 设置允许的跨域源。 */
        public Builder allowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = List.copyOf(Objects.requireNonNull(allowedOrigins, "allowedOrigins must not be null"));
            return this;
        }

        /** 设置允许的跨域请求方法。 */
        public Builder allowedMethods(List<String> allowedMethods) {
            this.allowedMethods = List.copyOf(Objects.requireNonNull(allowedMethods, "allowedMethods must not be null"));
            return this;
        }

        /** 设置允许的请求头。 */
        public Builder allowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = List.copyOf(Objects.requireNonNull(allowedHeaders, "allowedHeaders must not be null"));
            return this;
        }

        /** 设置可暴露的响应头。 */
        public Builder exposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = List.copyOf(Objects.requireNonNull(exposedHeaders, "exposedHeaders must not be null"));
            return this;
        }

        /** 设置是否允许携带凭证。 */
        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        /** 设置预检请求缓存时间（秒）。 */
        public Builder maxAge(int maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         * 产出不可变配置对象。
         *
         * @return 以当前收集值装配的新配置
         */
        public HttpCorsConfig build() {
            return new HttpCorsConfig(this);
        }
    }
}
