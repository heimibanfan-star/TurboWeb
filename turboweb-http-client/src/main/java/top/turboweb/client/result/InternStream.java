package top.turboweb.client.result;

/**
 * 内部使用的 Stream 扩展接口
 *
 * <p>
 * 用于由底层网络层通知流已经结束（正常或异常）
 * </p>
 */
public interface InternStream extends Stream {

    /**
     * 标记当前响应流已完成
     *
     * @param error
     *     正常结束时传 {@code null}，
     *     异常结束时传入对应异常
     */
    void completed(Throwable error);
}
