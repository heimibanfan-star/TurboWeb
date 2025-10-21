package top.turboweb.http.response;

/**
 * 内部调用响应接口。
 * <p>
 * 用于框架内部不同功能的调用场景。实现该接口的响应对象
 * 可以标识自身的内部调用类型，从而让框架在处理响应时选择合适的策略。
 * <p>
 * 典型使用场景包括：
 * <ul>
 *     <li>零拷贝文件传输</li>
 *     <li>流式文件响应</li>
 *     <li>服务器发送事件（SSE）</li>
 *     <li>基于 Reactor 的异步响应</li>
 *     <li>忽略响应处理等</li>
 * </ul>
 */
public interface InternalCallResponse {

    /**
     * 内部调用类型枚举。
     * <p>
     * 用于标识不同的内部调用响应策略。
     */
    enum InternalCallType {
        // 零拷贝
        ZERO_COPY,
        // 用于调用内部的文件流
        FILE_STREAM,
        // 基于AIO的文件响应
        AIO_FILE,
        // SSE
        SSE,
        // 默认的策略
        DEFAULT,
        // 基于Reactor的响应
        REACTOR,
        // 普通的流式调用
        STREAM,
        // 忽略对该响应的处理
        IGNORED
    }

    /**
     * 获取内部调用响应类型。
     *
     * @return 内部调用类型 {@link InternalCallType}
     */
    InternalCallType getType();
}
