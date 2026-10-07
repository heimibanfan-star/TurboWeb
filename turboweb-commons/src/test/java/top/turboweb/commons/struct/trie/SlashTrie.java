package top.turboweb.commons.struct.trie;

/**
 * 测试夹具：最简 "/" 分隔 Trie，不做任何子键归一化，用于直接观察
 * {@link AbstractTrie} 骨架契约（insert/get/delete/all/iterator）。
 *
 * <p>同包放置，因此测试可以直接访问 {@code protected} 的 {@code root}、
 * {@code Node} 访问器与 {@code splitKey}。
 */
class SlashTrie extends AbstractTrie<String, String> {

    @Override
    protected Details handleSubKey(String subKey) {
        return null;
    }

    @Override
    protected String splitString() {
        return "/";
    }

    @Override
    public String match(String key) {
        return get(key);
    }
}
