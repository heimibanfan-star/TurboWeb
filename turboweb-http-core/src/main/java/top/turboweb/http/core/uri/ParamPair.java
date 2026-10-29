package top.turboweb.http.core.uri;

import java.util.Objects;

/**
 * 查询参数的名值对：一个名称加一个可选值。
 * <p>
 * 值允许缺省（{@link #value()} 返回 {@code null}），
 * 缺省与空串在 URI 查询序列化时输出形态不同（仅有名称 versus 名称加等号）。
 */
public record ParamPair(String name, String value) {

    /**
     * 构造名值对。值可为 {@code null}。
     *
     * @param name  参数名称，不能为 {@code null}
     * @param value 参数值，可为 {@code null}
     * @throws NullPointerException 名称（name）为 {@code null} 时抛出
     */
    public ParamPair {
        Objects.requireNonNull(name, "name is null");
    }
}
