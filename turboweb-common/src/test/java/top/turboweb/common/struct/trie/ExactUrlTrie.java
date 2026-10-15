package top.turboweb.common.struct.trie;

/**
 * 测试夹具：只用来驱动 {@link UrlTrie#match(String)} 的拆分逻辑，
 * {@code doMatch} 直接落到精确 {@code get}。
 */
class ExactUrlTrie extends UrlTrie<String, String> {

    /**
     * 本夹具只关心 match 的拆分语义，归一化一律不做。
     *
     * <p>{@link AbstractTrie#handleSubKey} 是抽象方法且 {@link UrlTrie} 没有实现它，
     * 因此这里必须覆写，否则夹具无法编译。
     */
    @Override
    protected Details handleSubKey(String subKey) {
        return null;
    }

    @Override
    protected String doMatch(String[] segs) {
        // 空段数组对应 match("/")，而 get("") 会在 splitKey 里抛 IAE，必须先挡住
        return segs.length == 0 ? null : get(String.join("/", segs));
    }
}
