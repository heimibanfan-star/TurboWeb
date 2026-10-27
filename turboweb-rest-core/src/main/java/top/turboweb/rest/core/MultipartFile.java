package top.turboweb.rest.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传文件内容的读取契约：承载文件名、内容类型、内容长度与两条数据读取通道。
 *
 * <p>本接口只描述读取面，不承担释放义务：文件数据的生命周期由持有它的上游组件统一管理
 * （请求侧随请求处理结束回收），本接口的引用不得带出该生命周期。需要跨请求保留内容时，
 * 应在处理结束前经 {@link #readAll()} 或 {@link #inputStream()} 完成复制。</p>
 *
 * <p>{@link #inputStream()} 与 {@link #readAll()} 均自内容起始处读取；{@link #readAll()}
 * 面向体积可控的文件，大文件应经 {@link #inputStream()} 流式读取，避免一次性载入内存。</p>
 */
public interface MultipartFile {

    /**
     * 打开内容输入流，自内容起始处开始读取。
     *
     * @return 输入流，永不为 {@code null}；关闭义务归调用方
     * @throws IOException 打开或读取底层内容失败时抛出
     */
    InputStream inputStream() throws IOException;

    /**
     * 读取全部内容。
     *
     * <p>默认实现经 {@link #inputStream()} 读取并关闭流；底层内容可整体取用时，实现应覆盖
     * 本方法，免去多余的流包装。</p>
     *
     * @return 全部内容，永不为 {@code null}；空文件返回长度为 0 的数组
     * @throws IOException 读取底层内容失败时抛出
     */
    default byte[] readAll() throws IOException {
        try (InputStream in = inputStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * 获取原始文件名。
     *
     * @return 原始文件名；来源未提供文件名时返回 {@code null}
     */
    String filename();

    /**
     * 获取内容类型。
     *
     * @return 内容类型；未声明时返回 {@code null}
     */
    ContentType contentType();

    /**
     * 获取内容字节数。
     *
     * @return 内容长度，单位字节，非负
     */
    long length();

    /**
     * 判断内容是否为空。
     *
     * @return 内容长度为 0 时返回 true
     */
    default boolean isEmpty() {
        return length() == 0L;
    }
}
