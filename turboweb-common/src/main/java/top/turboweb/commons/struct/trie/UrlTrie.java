package top.turboweb.commons.struct.trie;

/**
 * 基于 URL 结构的前缀树（Trie）抽象类。
 *
 * <p>该类在通用 {@link AbstractTrie} 的基础上，针对以 <code>"/"</code> 分隔的路径结构
 * 进行了统一处理，例如：<code>/user/{id}</code> 或 <code>/files/**</code>。
 *
 * <p>子类可通过重写 {@link #doMatch(String[])} 实现不同的匹配逻辑，
 * 如支持通配符（*、**）、路径参数、正则匹配等。
 *
 * @param <T> 节点值类型
 * @param <M> 匹配结果类型（由子类定义）
 */
public abstract class UrlTrie <T, M> extends AbstractTrie<T, M> {

    private static final String SPLIT_STRING = "/";

    /**
     * 返回路径分隔符。
     *
     * @return 用于拆分路径的字符串（默认：<code>"/"</code>）
     */
    @Override
    protected String splitString() {
        return SPLIT_STRING;
    }

    /**
     * 根据 URL 规则进行匹配。
     *
     * <p>该方法会自动去除路径首尾的 <code>"/"</code>，并按段拆分后传入 {@link #doMatch(String[])}。
     *
     * <p>示例：
     * <pre>{@code
     * trie.match("/user/123");
     * }</pre>
     *
     * @param key 要匹配的路径（必须以 <code>"/"</code> 开头）
     * @return 匹配结果，若路径无效或未匹配到则返回 {@code null}
     */
    @Override
    public M match(String key) {
        if (key == null || !key.startsWith(SPLIT_STRING)) {
            // 无效匹配
            return null;
        }
        // 去除首尾的/
        StringBuilder stringBuilder = new StringBuilder(key);
        while (!stringBuilder.isEmpty() && stringBuilder.charAt(0) == '/') {
            stringBuilder.deleteCharAt(0);
        }
        while (!stringBuilder.isEmpty() && stringBuilder.charAt(stringBuilder.length() - 1) == '/') {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        // 再次判断是否为空字符
        String handledKey = stringBuilder.toString();
        String[] segs;
        if (handledKey.isEmpty()) {
            segs = new String[0];
        } else {
            segs = handledKey.split(SPLIT_STRING);
        }
        return doMatch(segs);
    }

    /**
     * 执行具体的匹配逻辑。
     *
     * <p>由子类实现以支持不同类型的路径匹配策略，
     * 如参数匹配、通配符匹配或正则匹配等。
     *
     * @param segs 路径分段数组（不包含首尾的空段）
     * @return 匹配结果
     */
    protected abstract M doMatch(String[] segs);
}
