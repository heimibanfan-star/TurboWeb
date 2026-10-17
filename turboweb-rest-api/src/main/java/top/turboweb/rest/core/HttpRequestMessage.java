package top.turboweb.rest.core;

import java.util.List;
import java.util.Set;

/**
 * http请求消息的读取契约：请求方式、请求地址与请求体内容的取用入口，并平铺承载
 * 请求头的读取访问。
 *
 * <p>请求头不设独立视图对象：泛型访问器与常用请求头便捷方法直接挂在消息上。头名
 * 查询大小写不敏感；便捷方法返回对应请求头的原始值（charset 等附加参数段原样保留），
 * 头未发送时返回 {@code null}，仅 {@link #contentLength()} 未发送或取值无效时记 0。
 * 请求体侧声明的内容类型经 {@link #content()} 返回的 {@link HttpContent} 读取。</p>
 */
public interface HttpRequestMessage {

    /**
     * 获取请求方式，取请求行的方法字面量。
     *
     * @return 请求方式
     */
    HttpRequestMethod method();

    /**
     * 获取请求地址。
     *
     * @return 请求地址，永不为 null
     */
    HttpUrl url();

    /**
     * 获取请求体内容。
     *
     * @return 请求体内容，永不为 null
     */
    HttpContent content();

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
     * 判断是否无任何请求头。
     *
     * @return 无头返回 true
     */
    boolean headerIsEmpty();

    /**
     * Content-Length 头取值。头缺失、非数值或为负时记 0。
     *
     * @return 请求体声明长度
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

    /** Host 头 */
    default String host() {
        return header("Host");
    }

    /** Connection 头 */
    default String connection() {
        return header("Connection");
    }

    /** Date 头 */
    default String date() {
        return header("Date");
    }

    /** Expect 头 */
    default String expect() {
        return header("Expect");
    }

    /** Accept 头 */
    default String accept() {
        return header("Accept");
    }

    /** Accept-Charset 头 */
    default String acceptCharset() {
        return header("Accept-Charset");
    }

    /** Accept-Encoding 头 */
    default String acceptEncoding() {
        return header("Accept-Encoding");
    }

    /** Accept-Language 头 */
    default String acceptLanguage() {
        return header("Accept-Language");
    }

    /** Authorization 头 */
    default String authorization() {
        return header("Authorization");
    }

    /** Cache-Control 头 */
    default String cacheControl() {
        return header("Cache-Control");
    }

    /**
     * Cookie 头原始值；结构化的 Cookie 逐项读写由实现侧的 Cookie 集合能力承接。
     *
     * @return 头原始值
     */
    default String cookie() {
        return header("Cookie");
    }

    /** Origin 头 */
    default String origin() {
        return header("Origin");
    }

    /** Referer 头 */
    default String referer() {
        return header("Referer");
    }

    /** User-Agent 头 */
    default String userAgent() {
        return header("User-Agent");
    }
}
