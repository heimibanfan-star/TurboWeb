package top.turboweb.client;

/**
 * 引擎传输失败异常：聚合请求在连接建立、请求发送或响应接收任一环节失败时抛出，
 * 原始失败原因以 cause 携带；流式请求的传输失败不经本类，经回调的
 * {@link StreamHandler#onThrowable(Throwable)} 递送。
 */
public class ClientTransportException extends RuntimeException {

    /**
     * 以说明与失败原因构造。
     *
     * @param message 失败说明
     * @param cause   原始失败原因
     */
    public ClientTransportException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 以说明构造。
     *
     * @param message 失败说明
     */
    public ClientTransportException(String message) {
        super(message);
    }
}
