package top.turboweb.http.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * http消息的读写契约：平铺承载消息行与消息体内容的读取，以及消息头的读取与写入修改访问；
 * 消息行的类型由子契约以泛型实参填充（如 http 请求消息以 {@link RequestLine} 填充，
 * http 响应消息以 {@link StatusLine} 填充），消息体内容统一为 {@link Content}。
 *
 * <p>消息头不设独立视图对象，读写访问直接平铺在消息上，头名查询大小写不敏感；
 * 头未发送时返回 {@code null}，仅 {@link #contentLength()} 未发送或取值无效时记 0。</p>
 *
 * @param <LINE> 消息行信息的类型
 */
public interface HttpMessage<LINE> {

    /**
     * 获取消息行信息：请求消息为请求行，响应消息为状态行。
     *
     * @return 消息行信息
     */
    LINE line();

    /**
     * 获取消息体内容。
     *
     * @return 消息体内容
     */
    Content content();

    /**
     * 获取指定头名的首个取值。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @return 首个取值；头不存在时返回 {@code null}
     */
    String header(String name);

    /**
     * 获取指定头名的全部取值，按出现顺序。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @return 取值列表；头不存在时返回空列表
     */
    List<String> headerValues(String name);

    /**
     * 获取全部消息头的键值对：键为头名首现原文，值为该头名的取值序列。
     * <p>取值序列为只读视图，写入须走消息的写通道；不含空取值列表的头条目。</p>
     *
     * @return 消息头键值对集合
     */
    Map<String, List<String>> headers();

    /**
     * 判断指定头名是否存在。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @return 存在返回 true
     */
    boolean containsHeader(String name);

    /**
     * 获取全部头名，按发送原文并去重。
     *
     * @return 头名集合
     */
    Set<String> headerNames();

    /**
     * 获取头条目数，同名多值按条目分别计数。
     *
     * @return 条目数
     */
    int headerSize();

    /**
     * 判断是否无任何消息头。
     *
     * @return 无头返回 true
     */
    boolean headerIsEmpty();

    /**
     * 设置指定头名的取值：覆盖同名（大小写不敏感）的全部既有取值，头名按入参原文记录。
     *
     * @param name  头名，大小写不敏感，不能为空
     * @param value 取值，不能为空
     * @throws NullPointerException 当 {@code name} 或 {@code value} 为 {@code null} 时抛出
     */
    void setHeader(String name, String value);

    /**
     * 追加一条取值：同名头（大小写不敏感）已存在时并入既有条目并保持头名首现原文，
     * 不存在时按入参原文新建条目。
     *
     * @param name  头名，大小写不敏感，不能为空
     * @param value 取值，不能为空
     * @throws NullPointerException 当 {@code name} 或 {@code value} 为 {@code null} 时抛出
     */
    void addHeader(String name, String value);

    /**
     * 删除指定头名的全部取值，头名大小写不敏感；头不存在时无操作。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @throws NullPointerException 当 {@code name} 为 {@code null} 时抛出
     */
    void removeHeader(String name);

    /**
     * 清空全部消息头。
     */
    void clearHeaders();

    /**
     * Content-Length 头取值。头缺失、非数值或为负时记 0。
     *
     * @return 消息体声明长度
     */
    default long contentLength() {
        String value = header("Content-Length");
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(Long.parseLong(value.trim()), 0L);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
