package top.turboweb.http.core;

import java.util.Objects;

/**
 * 字节数组型消息体内容：以堆上字节数组持有消息体。
 * <p>数组是堆上对象，生命周期由 GC 管理，无需要归还的原生内存，不背负关闭义务。</p>
 */
public class ByteContent {

    /** 持有的字节数组本体 */
    private final byte[] bytes;

    /**
     * 建立字节数组内容。
     *
     * @param bytes 消息体字节数组，按入参原样持有，不作复制，不能为空
     * @throws NullPointerException 当 {@code bytes} 为 {@code null} 时抛出
     */
    public ByteContent(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes must not be null");
    }

    /**
     * 获取持有的字节数组本体。
     *
     * @return 构造期持有的字节数组，不作复制
     */
    public byte[] bytes() {
        return bytes;
    }
}
