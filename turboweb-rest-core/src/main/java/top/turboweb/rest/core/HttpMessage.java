package top.turboweb.rest.core;

import java.util.List;
import java.util.Set;

/**
 * http消息的读取契约：平铺承载消息头的读取访问，行信息与消息体内容的类型由
 * 子契约以泛型实参填充（如 http 请求消息以 {@link RequestLine} 与 {@link HttpContent} 填充）。
 *
 * <p>消息头不设独立视图对象，读取访问直接平铺在消息上，头名查询大小写不敏感；
 * 头未发送时返回 {@code null}，仅 {@link #contentLength()} 未发送或取值无效时记 0。</p>
 *
 * @param <LINE>    消息行信息的类型
 * @param <CONTENT> 消息体内容的类型
 */
public interface HttpMessage<LINE, CONTENT> {

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
