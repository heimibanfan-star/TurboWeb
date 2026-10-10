package top.turboweb.struct.radix;

/**
 * 纯基数树契约：一次匹配解析出单个元素，不含任何 REST 概念。
 *
 * @param <O> 值类型
 */
public interface RadixTree<O> {

    /**
     * 匹配目标路径。
     *
     * @param path 目标路径，不得为 null
     * @return 命中元素；未命中返回 null
     */
    O match(String path);
}
