package top.turboweb.struct.radix;

import java.util.Map;

/**
 * REST 路径基数树契约：路径段支持 {@code :name} 参数占位，
 * 匹配命中时返回携带参数绑定的 {@link MatchResult}。
 *
 * @param <O> 值类型
 */
public interface RestRadixTree<O> extends RadixTree<RestRadixTree.MatchResult<O>> {

    /**
     * 匹配命中结果：命中模式上注册的值与路径参数绑定。
     *
     * @param <O> 值类型
     */
    final class MatchResult<O> {

        /**
         * 命中模式上注册的值。
         */
        public final O value;

        /**
         * 路径参数绑定（参数名到路径段），不可变。
         */
        public final Map<String, String> params;

        MatchResult(O value, Map<String, String> params) {
            this.value = value;
            this.params = Map.copyOf(params);
        }
    }
}
