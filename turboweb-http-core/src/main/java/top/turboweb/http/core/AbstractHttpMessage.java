package top.turboweb.http.core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link HttpMessage} 头部操作的全量实现：自带头存储，构造后即可读写，
 * 子类无需再实现任何头部访问器。
 *
 * <p>头名查询与写入修改均大小写不敏感；头名按首现原文记录，大小写变体的写入
 * 并入既有条目，覆盖式写入则改按写入原文记录；同名取值按出现顺序保持。
 * 消息行与消息体内容亦由本类存储，构造期经子类构造器传入。
 * 本类按消息单线程使用，不加同步。</p>
 *
 * @param <LINE>    消息行信息的类型
 * @param <CONTENT> 消息体内容的类型
 */
public abstract class AbstractHttpMessage<LINE, CONTENT> implements HttpMessage<LINE, CONTENT> {

    /**
     * 自有的头存储：键为头名首现原文，值为该头名的取值序列
     */
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    /**
     * 构造期持有的消息行
     */
    private final LINE line;

    /**
     * 构造期持有的消息体内容；无体消息为 {@code null}
     */
    private final CONTENT content;

    /**
     * 供子类建立携带消息体的消息，持有消息行与消息体内容，头存储初始为空。
     *
     * @param line    消息行信息，不能为空
     * @param content 消息体内容，不能为空
     * @throws NullPointerException 当 {@code line} 或 {@code content} 为 {@code null} 时抛出
     */
    protected AbstractHttpMessage(LINE line, CONTENT content) {
        this.line = Objects.requireNonNull(line, "line must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * 供子类建立无消息体的消息（内容类型为 {@link Void}），头存储初始为空，
     * {@link #content()} 恒返回 {@code null}。
     *
     * @param line 消息行信息，不能为空
     * @throws NullPointerException 当 {@code line} 为 {@code null} 时抛出
     */
    protected AbstractHttpMessage(LINE line) {
        this.line = Objects.requireNonNull(line, "line must not be null");
        this.content = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LINE line() {
        return line;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CONTENT content() {
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String header(String name) {
        List<String> values = valuesOf(name);
        return values == null ? null : values.getFirst();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> headers() {
        return headers.entrySet()
                .stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Collections.unmodifiableList(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> headerValues(String name) {
        List<String> values = valuesOf(name);
        return values == null ? List.of() : Collections.unmodifiableList(values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {
        return valuesOf(name) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> headerNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(headers.keySet()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int headerSize() {
        int size = 0;
        for (List<String> values : headers.values()) {
            size += values.size();
        }
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean headerIsEmpty() {
        return headers.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        removeHeader(name);
        headers.put(name, new ArrayList<>(List.of(value)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, String value) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        String key = keyOf(name);
        if (key == null) {
            headers.put(name, new ArrayList<>(List.of(value)));
        } else {
            headers.get(key).add(value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeHeader(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String key = keyOf(name);
        if (key != null) {
            headers.remove(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearHeaders() {
        headers.clear();
    }

    /**
     * 按大小写不敏感查找既有条目的头名原文。
     *
     * @param name 头名，大小写不敏感
     * @return 既有条目的头名原文；头不存在时返回 {@code null}
     */
    private String keyOf(String name) {
        for (String key : headers.keySet()) {
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
        return key == null ? null : headers.get(key);
    }
}
