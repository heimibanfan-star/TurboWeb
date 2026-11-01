package top.turboweb.http.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * http 消息头的存储与读写容器：头名查询与写入修改均大小写不敏感；
 * 头名按首现原文记录，大小写变体的写入并入既有条目，覆盖式写入则改按写入原文记录；
 * 同名取值按出现顺序保持。本类按单线程使用，不加同步。
 */
public class Headers {

    /**
     * 头存储：键为头名首现原文，值为该头名的取值序列
     */
    private final Map<String, List<String>> entries = new LinkedHashMap<>();

    /**
     * 建立无任何头的容器。
     */
    public Headers() {
    }

    /**
     * 建立携带既有全部头条目的容器：条目逐组深拷贝，拷贝后与源互不影响。
     *
     * @param source 源容器，不能为 {@code null}
     * @throws NullPointerException 当 {@code source} 为 {@code null} 时抛出
     */
    public Headers(Headers source) {
        Objects.requireNonNull(source, "source must not be null");
        for (Map.Entry<String, List<String>> entry : source.entries.entrySet()) {
            this.entries.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    /**
     * 获取指定头名的首个取值。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @return 首个取值；头不存在时返回 {@code null}
     */
    public String get(String name) {
        List<String> values = valuesOf(name);
        return values == null ? null : values.getFirst();
    }

    /**
     * 获取指定头名的全部取值，按出现顺序。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @return 取值列表；头不存在时返回空列表
     */
    public List<String> values(String name) {
        List<String> values = valuesOf(name);
        return values == null ? List.of() : Collections.unmodifiableList(values);
    }

    /**
     * 获取全部消息头的键值对快照：键为头名首现原文，值为该头名的取值序列。
     * <p>键值对与取值序列均为只读视图，不含空取值列表的头条目；后续写入不在快照中可见。</p>
     *
     * @return 消息头键值对集合
     */
    public Map<String, List<String>> asMap() {
        return Collections.unmodifiableMap(entries.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Collections.unmodifiableList(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                )));
    }

    /**
     * 判断指定头名是否存在。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @return 存在返回 true
     */
    public boolean contains(String name) {
        return valuesOf(name) != null;
    }

    /**
     * 获取全部头名，按发送原文并去重。
     *
     * @return 头名集合
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(entries.keySet()));
    }

    /**
     * 获取头条目数，同名多值按条目分别计数。
     *
     * @return 条目数
     */
    public int size() {
        int size = 0;
        for (List<String> values : entries.values()) {
            size += values.size();
        }
        return size;
    }

    /**
     * 判断是否无任何消息头。
     *
     * @return 无头返回 true
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Content-Length 头取值。头缺失、非数值或为负时记 0。
     *
     * @return 消息体声明长度
     */
    public long contentLength() {
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

    /**
     * 设置指定头名的取值：覆盖同名（大小写不敏感）的全部既有取值，头名按入参原文记录。
     *
     * @param name  头名，大小写不敏感，不能为空
     * @param value 取值，不能为空
     * @throws NullPointerException 当 {@code name} 或 {@code value} 为 {@code null} 时抛出
     */
    public void set(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        remove(name);
        entries.put(name, new ArrayList<>(List.of(value)));
    }

    /**
     * 追加一条取值：同名头（大小写不敏感）已存在时并入既有条目并保持头名首现原文，
     * 不存在时按入参原文新建条目。
     *
     * @param name  头名，大小写不敏感，不能为空
     * @param value 取值，不能为空
     * @throws NullPointerException 当 {@code name} 或 {@code value} 为 {@code null} 时抛出
     */
    public void add(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        String key = keyOf(name);
        if (key == null) {
            entries.put(name, new ArrayList<>(List.of(value)));
        } else {
            entries.get(key).add(value);
        }
    }

    /**
     * 删除指定头名的全部取值，头名大小写不敏感；头不存在时无操作。
     *
     * @param name 头名，大小写不敏感，不能为空
     * @throws NullPointerException 当 {@code name} 为 {@code null} 时抛出
     */
    public void remove(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String key = keyOf(name);
        if (key != null) {
            entries.remove(key);
        }
    }

    /**
     * 清空全部消息头。
     */
    public void clear() {
        entries.clear();
    }

    /**
     * 按大小写不敏感查找既有条目的头名原文。
     *
     * @param name 头名，大小写不敏感
     * @return 既有条目的头名原文；头不存在时返回 {@code null}
     */
    private String keyOf(String name) {
        for (String key : entries.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    /**
     * 按大小写不敏感查找既有条目的取值序列。
     *
     * @param name 头名，大小写不敏感
     * @return 取值序列；头不存在时返回 {@code null}
     */
    private List<String> valuesOf(String name) {
        String key = keyOf(name);
        return key == null ? null : entries.get(key);
    }
}
