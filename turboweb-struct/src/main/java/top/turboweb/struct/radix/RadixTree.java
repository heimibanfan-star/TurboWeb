package top.turboweb.struct.radix;

import java.util.Set;

/**
 * 基数树读契约：精确读取、存在性判断、前缀多命中收集与任一命中读取。
 * <p>树中的值不允许为 null，因此读方法返回 null 即表示键不存在。
 * 实现只实现与自身匹配语义相符的方法，其余方法抛出 {@link UnsupportedOperationException}。
 *
 * @param <O> 值类型
 */
public interface RadixTree<O> {

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

    /**
     * 任一命中读取：返回一个命中值，取哪个由实现定义；无命中返回 null。
     *
     * @param key 目标键，不得为 null
     * @return 任一命中的值；无命中时返回 null
     */
    O matchAny(CharSequence key);
}
