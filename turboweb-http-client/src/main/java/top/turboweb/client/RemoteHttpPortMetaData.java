package top.turboweb.client;

import top.turboweb.http.core.ByteRequestMessage;
import top.turboweb.http.core.Headers;
import top.turboweb.http.core.HttpRequestMethod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 远程端口元数据：定型持有一次远程端口调用的目标、扩展头、请求方式
 * 与请求构建回调，建造期一次赋值，构建后不可再修改。
 *
 * <p>扩展头映射为头名到多值数组的条目集合；映射与各数组在建造时防御性拷贝，
 * 外部后续修改不影响元数据持有的内容。
 * {@link #message(Object[])} 按定型扩展头填充请求头实体，
 * 再交由回调构建出请求消息，请求构建的发起归调用方。</p>
 */
public final class RemoteHttpPortMetaData {

    /**
     * 请求构建回调：按请求头实体与调用实参构建请求消息。
     */
    @FunctionalInterface
    public interface RequestCallback {

        /**
         * 构建请求消息。
         *
         * @param headers 请求头实体，已按定型扩展头填充
         * @param args    调用实参数组
         * @return 构建出的请求消息
         */
        ByteRequestMessage build(Headers headers, Object[] args);
    }

    private final String target;
    private final Map<String, String[]> extendHeaders;
    private final HttpRequestMethod requestMethod;
    private final RequestCallback callback;

    /**
     * 按定型属性建立元数据，扩展头映射与各值数组在此防御性拷贝。
     *
     * @param target         请求目标
     * @param extendHeaders  扩展头映射，不能为 {@code null}，元素数组与取值也不能为 {@code null}
     * @param requestMethod  请求方式，不能为 {@code null}
     * @param callback       请求构建回调，不能为 {@code null}
     * @throws NullPointerException 当任一属性或映射条目为 {@code null} 时抛出
     */
    public RemoteHttpPortMetaData(String target,
                                  Map<String, String[]> extendHeaders,
                                  HttpRequestMethod requestMethod,
                                  RequestCallback callback) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(extendHeaders, "extendHeaders must not be null");
        extendHeaders.forEach((name, values) -> {
            Objects.requireNonNull(name, "extend header name must not be null");
            Objects.requireNonNull(values, "extend header values must not be null");
            for (String value : values) {
                Objects.requireNonNull(value, "extend header value must not be null");
            }
        });
        this.extendHeaders = deepCopy(extendHeaders);
        this.requestMethod = Objects.requireNonNull(requestMethod, "requestMethod must not be null");
        this.callback = Objects.requireNonNull(callback, "callback must not be null");
    }

    /** 请求目标，建造期定型。 */
    public String target() {
        return target;
    }

    /** 请求方式，建造期定型。 */
    public HttpRequestMethod method() {
        return requestMethod;
    }

    /**
     * 构建请求消息：建立请求头实体并逐条填入定型扩展头（同名多值按序追加），
     * 再交由回调按调用实参构建请求消息。
     *
     * @param args 调用实参数组，不能为 {@code null}
     * @return 回调构建出的请求消息
     * @throws NullPointerException 当 {@code args} 为 {@code null} 时抛出
     */
    public ByteRequestMessage message(Object[] args) {
        Objects.requireNonNull(args, "args must not be null");
        Headers headers = new Headers();
        extendHeaders.forEach((name, values) -> {
            for (String value : values) {
                headers.add(name, value);
            }
        });
        return callback.build(headers, args);
    }

    /**
     * 深拷贝扩展头映射：条目按原顺序转入新映射，各值数组逐个克隆。
     */
    private static Map<String, String[]> deepCopy(Map<String, String[]> source) {
        Map<String, String[]> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, values.clone()));
        return copy;
    }
}
