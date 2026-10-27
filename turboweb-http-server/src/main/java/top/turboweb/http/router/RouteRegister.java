package top.turboweb.http.router;

import top.turboweb.serializer.JsonSerializer;

/**
 * 路由登记表接口：持有路由与各项扩展点的可配置面，按给定序列化器生产路由处理器管理器。
 *
 * <p>登记表是管理器的生产方：调用方只在登记表上登记与配置，管理器的装配动作与装配时序
 * 由 {@link #newRouteHandlerManager(JsonSerializer)} 承担，生产出的管理器其内容为本登记表
 * 生产时刻的快照，此后与本登记表再无关联。
 *
 * <p>生产一次即冻结：同一登记表至多生产一个管理器，生产之后登记表不再接受任何变更，
 * 变更类入口一律抛出 {@link IllegalStateException}。
 */
public interface RouteRegister {

    /**
     * 以给定 json 序列化器生产路由处理器管理器
     *
     * @param jsonSerializer json 序列化器，不能为 null
     * @return 路由处理器管理器，其内容为本登记表生产时刻的快照
     * @throws NullPointerException  序列化器为 null
     * @throws IllegalStateException 本登记表已生产过管理器
     */
    RouteHandlerManager newRouteHandlerManager(JsonSerializer jsonSerializer);
}
