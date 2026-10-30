package top.turboweb.http.connection;

import io.netty.handler.codec.http.FullHttpRequest;

import java.util.Objects;

/**
 * 序列化入站请求信使：定序处理器为请求分配自增序列号后以本信使封章，向下游派发，
 * 下游凭序列号建立携带该序列号的连接，响应写出时封章定序。
 * <p>信使为不可变对象，不持有任何资源；承载的请求对象的所有权随信使转移，
 * 由接收方按 Netty 引用计数口径管理。</p>
 */
public final class SequencedRequest {

    /** 本请求的序列号 */
    private final long sequence;

    /** 承载的原始请求 */
    private final FullHttpRequest request;

    /**
     * 建立序列化入站请求信使。
     *
     * @param sequence 本请求的序列号
     * @param request  原始请求，不能为空
     * @throws NullPointerException 当 {@code request} 为 {@code null} 时抛出
     */
    public SequencedRequest(long sequence, FullHttpRequest request) {
        this.sequence = sequence;
        this.request = Objects.requireNonNull(request, "request can not be null");
    }

    /**
     * 获取本请求的序列号。
     *
     * @return 序列号
     */
    public long sequence() {
        return sequence;
    }

    /**
     * 获取承载的原始请求。
     *
     * @return 原始请求
     */
    public FullHttpRequest request() {
        return request;
    }
}
