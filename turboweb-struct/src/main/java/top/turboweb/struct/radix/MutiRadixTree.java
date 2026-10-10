package top.turboweb.struct.radix;

import java.util.Set;

/**
 * 基数树的只读契约：精确读取与前缀匹配收集。
 * <p>树中的值不允许为 null，因此读方法返回 null 即表示键不存在。
 *
 * @param <O> 值类型
 */
public interface MutiRadixTree<O> {

    /**
     * 精确匹配取值。
     *
     * @param key 目标键，不得为 null
     * @return 键对应的值；键未注册时返回 null
     */
    O get(CharSequence key);

    /**
     * 精确匹配存在性判断。
     *
     * @param key 目标键，不得为 null
     * @return 键已注册返回 true，否则返回 false
     */
    boolean exist(CharSequence key);

    /**
     * 前缀匹配收集：取树中所有作为 key 前缀的已注册键的值，
     * key 自身已注册时其值包含在内（完全相等是前缀的特例）。
     *
     * @param key 目标键，不得为 null
     * @return 命中值的集合；无任何命中时返回空集，不返回 null
     */
    Set<O> match(CharSequence key);
}
