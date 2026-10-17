package top.turboweb.rest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 作为命名空间，内部声明HttpContext专用的组件
 */
public interface Http {

    /**
     * 请求体内容的读取契约：声明体侧内容类型，不携带关闭义务。
     * 无显式释放需求的实现只实现本接口即可。
     */
    interface Content {

        ContentType contentType();
    }

    /**
     * http请求消息：请求方式、请求头与请求地址的读取入口。
     */
    interface Message {

        /**
         * 获取请求方式，取请求行的方法字面量。
         *
         * @return 请求方式（标准方法为大写，如 {@code GET}、{@code POST}）
         */
        String method();

        /**
         * 获取请求头只读视图。
         *
         * @return 请求头
         */
        Headers headers();

        /**
         * 获取请求地址。
         *
         * @return 请求地址
         */
        URL url();
    }

    /**
     * 请求地址：承载请求行的原始 URI（含路径，可带查询段与片段段），
     * 提供地址取用与查询参数解码两个通道。
     */
    interface URL {

        /**
         * 获取请求地址。
         *
         * @param pathOnly 为 {@code true} 时仅获取地址本体：自第一个 {@code ?} 或 {@code #}
         *                 处截断，不携带查询参数与片段；为 {@code false} 时返回原始地址
         * @return 请求地址字符串
         */
        String url(boolean pathOnly);

        /**
         * 获取查询参数：解析地址查询段，键按首现顺序排列，同名参数取值按出现顺序保留；
         * 无等号段（如 {@code ?flag}）取值为 {@code null}；百分号转义按装配期缺省字符集
         * 解码，转义畸形时抛出 {@link IllegalArgumentException}。
         *
         * @return 参数名到值列表的不可变映射；无查询段时返回空 Map
         */
        Map<String, List<String>> queryParams();
    }

    /**
     * 请求头：只读视图，头名查询大小写不敏感。泛型访问器参照 Netty HttpHeaders 的
     * 只读口径（取首值、取全部值、包含判断、名称枚举、条目计数）；常用请求头便捷
     * 方法返回原始头值（charset 等附加参数段原样保留），头未发送时返回 {@code null}，
     * 仅 {@link #contentLength()} 未发送或取值无效时记 0。
     */
    interface Headers {

        /**
         * 获取指定头名的首个取值。
         *
         * @param name 头名，大小写不敏感，不能为空
         * @return 首个取值；头不存在时返回 {@code null}
         */
        String get(String name);

        /**
         * 获取指定头名的全部取值，按出现顺序。
         *
         * @param name 头名，大小写不敏感，不能为空
         * @return 取值列表；头不存在时返回空列表
         */
        List<String> getAll(String name);

        /**
         * 判断头是否存在。
         *
         * @param name 头名，大小写不敏感，不能为空
         * @return 存在返回 true
         */
        boolean contains(String name);

        /**
         * 获取全部头名，按发送原文并去重。
         *
         * @return 头名集合
         */
        Set<String> names();

        /**
         * 获取头条目数，同名多值按条目分别计数。
         *
         * @return 条目数
         */
        int size();

        /**
         * 判断是否无任何请求头。
         *
         * @return 无头返回 true
         */
        boolean isEmpty();

        /** Content-Type 头原始值 */
        default String contentType() {
            return get("Content-Type");
        }

        /**
         * Content-Length 头取值。头缺失、非数值或为负时记 0。
         *
         * @return 请求体声明长度
         */
        default long contentLength() {
            String value = get("Content-Length");
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
            return get("Host");
        }

        /** Connection 头 */
        default String connection() {
            return get("Connection");
        }

        /** Date 头 */
        default String date() {
            return get("Date");
        }

        /** Expect 头 */
        default String expect() {
            return get("Expect");
        }

        /** Accept 头 */
        default String accept() {
            return get("Accept");
        }

        /** Accept-Charset 头 */
        default String acceptCharset() {
            return get("Accept-Charset");
        }

        /** Accept-Encoding 头 */
        default String acceptEncoding() {
            return get("Accept-Encoding");
        }

        /** Accept-Language 头 */
        default String acceptLanguage() {
            return get("Accept-Language");
        }

        /** Authorization 头 */
        default String authorization() {
            return get("Authorization");
        }

        /** Cache-Control 头 */
        default String cacheControl() {
            return get("Cache-Control");
        }

        /**
         * Cookie 头原始值；结构化的 Cookie 读写走上下文侧的 Cookie 集合接口。
         *
         * @return 头原始值
         */
        default String cookie() {
            return get("Cookie");
        }

        /** Origin 头 */
        default String origin() {
            return get("Origin");
        }

        /** Referer 头 */
        default String referer() {
            return get("Referer");
        }

        /** User-Agent 头 */
        default String userAgent() {
            return get("User-Agent");
        }
    }
}
