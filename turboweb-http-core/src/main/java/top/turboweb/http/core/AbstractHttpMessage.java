package top.turboweb.http.core;

import java.util.Objects;

/**
 * {@link HttpMessage} 的基础实现：构造后即可持有消息行、消息体内容与消息头实体，
 * 子类无需再实现任何访问器。消息头读写经 {@link #headers()} 返回的
 * {@link Headers} 实体进行，头名大小写不敏感等头语义由 {@link Headers} 承载。
 * 消息行与消息体内容亦由本类存储，构造期经子类构造器传入。
 * 本类按消息单线程使用，不加同步。
 *
 * @param <LINE>    消息行信息的类型
 * @param <CONTENT> 消息体内容的类型
 */
public abstract class AbstractHttpMessage<LINE, CONTENT> implements HttpMessage<LINE, CONTENT> {

    /**
     * 消息自有的头实体：读写直接作用于消息
     */
    private final Headers headers = new Headers();

    /**
     * 构造期持有的消息行
     */
    private final LINE line;

    /**
     * 构造期持有的消息体内容；无体消息为 {@code null}
     */
    private final CONTENT content;

    /**
     * 供子类建立携带消息体的消息，持有消息行与消息体内容，头实体初始为空。
     *
     * @param line    消息行信息，不能为空
     * @param content 消息体内容，不能为空
     * @throws NullPointerException 当 {@code line} 或 {@code content} 为 {@code null} 时抛出
     */
    protected AbstractHttpMessage(LINE line, CONTENT content) {
        this.line = Objects.requireNonNull(line, "line must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * 供子类建立无消息体的消息（内容类型为 {@link Void}），头实体初始为空，
     * {@link #content()} 恒返回 {@code null}。
     *
     * @param line 消息行信息，不能为空
     * @throws NullPointerException 当 {@code line} 为 {@code null} 时抛出
     */
    protected AbstractHttpMessage(LINE line) {
        this.line = Objects.requireNonNull(line, "line must not be null");
        this.content = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LINE line() {
        return line;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CONTENT content() {
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Headers headers() {
        return headers;
    }
}
