package top.turboweb.rest;

import java.util.List;
import java.util.Map;

/**
 * 请求地址：承载请求行的原始 URI（含路径，可带查询段与片段段），
 * 提供地址取用与查询参数解码两个通道。
 */
public interface HttpUrl {

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
