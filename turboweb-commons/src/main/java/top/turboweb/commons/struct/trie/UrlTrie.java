package top.turboweb.commons.struct.trie;

/**
 * 基于url的trie
 */
public abstract class UrlTrie <T, M> extends AbstractTrie<T, M> {

    private static final String SPLIT_STRING = "/";

    @Override
    protected Details handleSubKey(String subKey) {
        return null;
    }

    @Override
    protected String splitString() {
        return SPLIT_STRING;
    }

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
     * 匹配
     * @param segs 分割的字符串
     * @return 匹配结果
     */
    protected abstract M doMatch(String[] segs);
}
