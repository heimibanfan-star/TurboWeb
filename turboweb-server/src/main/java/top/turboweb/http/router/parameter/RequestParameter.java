package top.turboweb.http.router.parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 非请求体参数承载:路径参数(路由匹配产物)与查询参数(URI 查询串解析产物)两份映射,
 * 由路由装配解码就位后随处理器调用传递,交各参数绑定器取用。
 * <p>
 * 纯字符串视图:类型转换由绑定器侧 {@code ParameterHandleHelper} 承担,本承载不做转换、
 * 不依赖序列化。无等号查询段(如 {@code ?flag})的取值为 null,按序保留在值列表中。
 * 两份映射与内层值列表均为装配期拷贝后的不可变视图。
 */
public record RequestParameter(Map<String, String> pathParams, Map<String, List<String>> queryParams) {

    public RequestParameter {
        Map<String, List<String>> queryCopy = new LinkedHashMap<>();
        queryParams.forEach((name, values) ->
                // 内层拷 ArrayList 再包不可变视图:值可为 null,不能用 List.copyOf
                queryCopy.put(name, Collections.unmodifiableList(new ArrayList<>(values))));
        pathParams = Collections.unmodifiableMap(new LinkedHashMap<>(pathParams));
        queryParams = Collections.unmodifiableMap(queryCopy);
    }

    /**
     * 路径参数取值(装配期已按缺省字符集解码)
     *
     * @param name 参数名
     * @return 取值;缺名返回 null
     */
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /**
     * 查询参数首值
     *
     * @param name 参数名
     * @return 首个取值;缺名返回 null;{@code ?flag} 无等号段的取值为 null
     */
    public String queryParam(String name) {
        List<String> values = queryParams.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * 查询参数全部取值,按出现顺序
     *
     * @param name 参数名
     * @return 值列表(不可变);缺名返回空列表
     */
    public List<String> queryParams(String name) {
        return queryParams.getOrDefault(name, List.of());
    }
}
