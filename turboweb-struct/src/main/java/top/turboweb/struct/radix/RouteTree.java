package top.turboweb.struct.radix;

import java.util.Map;

/**
 * 路由树读契约：按注册模式对路径做单命中匹配，命中返回携带参数绑定的结果。
 * <p>实现负责模式注册与命中择优；匹配以整段对齐为准，命中判定与择优口径由实现定义。
 *
 * @param <O> 值类型
 */
public interface RouteTree<O> {

    /**
     * 匹配命中结果：命中模式上注册的值与路径参数绑定。
     *
     * @param <O>    值类型
     * @param value  命中模式上注册的值
     * @param params 路径参数绑定（参数名到路径段），不可变
     */
    record Result<O>(O value, Map<String, String> params) {

        /**
         * 归一参数绑定为不可变映射。
         */
        public Result {
            params = Map.copyOf(params);
        }
    }

    /**
     * 单命中匹配：返回 key 命中的注册模式上的值与参数绑定，只取单个命中。
     *
     * @param key 目标键，不得为 null
     * @return 命中结果；无命中时返回 null
     */
    Result<O> match(CharSequence key);
}
