package top.turboweb.http.connection;

import java.util.Objects;

/**
 * 序列化出站数据信使：请求在调度期获得自增序列号，其响应经由连接写出的每条消息
 * 都以本信使承载，写出侧凭序列号判断该消息是否轮到直写或须缓冲定序。
 * <p>信使为不可变对象，仅承载序列号与原始出站消息，不持有任何资源；
 * 出站消息的所有权随写出自携，由管道按 Netty 引用计数口径管理。</p>
 */
public final class SequencedDataChunk {

    /** 本消息所属响应的序列号 */
    private final long sequence;

    /** 承载的原始出站消息 */
    private final Object message;

    /**
     * 建立序列化出站数据信使。
     *
     * @param sequence 本消息所属响应的序列号
     * @param message  原始出站消息，不能为空
     * @throws NullPointerException 当 {@code message} 为 {@code null} 时抛出
     */
    public SequencedDataChunk(long sequence, Object message) {
        this.sequence = sequence;
        this.message = Objects.requireNonNull(message, "message can not be null");
    }

    /**
     * 获取本消息所属响应的序列号。
     *
     * @return 序列号
     */
    public long sequence() {
        return sequence;
    }

    /**
     * 获取承载的原始出站消息。
     *
     * @return 原始出站消息
     */
    public Object message() {
        return message;
    }
}
