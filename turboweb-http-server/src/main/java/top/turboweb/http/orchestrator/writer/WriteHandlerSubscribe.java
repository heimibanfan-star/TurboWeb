package top.turboweb.http.orchestrator.writer;

/**
 * 响应消息的写出处理器订阅声明：实现本接口的消息声明由哪个
 * {@link MessageWriteHandler} 实现负责写出自身（订阅者），
 * 响应消息写出器据此先查内置策略组、未命中再查自定义策略组；
 * 未实现本接口、或声明的订阅者类型两级均未命中的消息落降级区间。
 */
public interface WriteHandlerSubscribe {

    /**
     * 获取订阅本消息写出职责的处理器类型。
     *
     * @return 订阅者实现类的 Class 对象
     */
    Class<? extends MessageWriteHandler> subscriber();
}
