package top.turboweb.client.result;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.charset.Charset;
import java.util.concurrent.Future;

/**
 * 以流的形式返回 HTTP 响应结果
 *
 * <p>
 * 该接口支持：
 * <ul>
 *     <li>按 ByteBuf / byte[] 流式读取</li>
 *     <li>按字符串（Charset）流式解码读取</li>
 *     <li>阻塞式拉取数据，直到有新数据或流结束</li>
 * </ul>
 *
 * <strong>注意：</strong>
 * 同一个 Stream 实例只能选择一种读取方式（字节 or 字符串），
 * 混用会导致异常。
 * </p>
 */
public interface Stream {

    /**
     * 获取 HTTP 响应状态码
     *
     * @return 响应状态
     */
    HttpResponseStatus status();

    /**
     * 获取 HTTP 响应头
     *
     * @return 响应头
     */
    HttpHeaders headers();

    /**
     * 获取下一段响应内容（ByteBuf）
     *
     * <p>
     * 默认阻塞获取。
     * 当返回 {@code null} 时，表示响应流已结束。
     * </p>
     *
     * @return 下一段内容，或 {@code null}
     */
    ByteBuf nextContent();

    /**
     * 获取下一段响应内容（ByteBuf）
     *
     * @param allowLazyTime 允许的最大阻塞等待时间（毫秒）
     * @return 下一段内容，或 {@code null}
     */
    ByteBuf nextContent(long allowLazyTime);

    /**
     * 获取下一段响应内容（字节数组）
     *
     * <p>
     * 内部会自动释放 ByteBuf
     * </p>
     *
     * @return 下一段字节数据，或 {@code null}
     */
    byte[] nextBytes();

    /**
     * 获取下一段响应内容（字节数组）
     *
     * @param allowLazyTime 允许的最大阻塞等待时间（毫秒）
     * @return 下一段字节数据，或 {@code null}
     */
    byte[] nextBytes(long allowLazyTime);

    /**
     * 按指定字符集获取下一段字符串
     *
     * <p>
     * 支持跨 ByteBuf 的字符解码（例如 UTF-8 半个字符的情况）
     * </p>
     *
     * @param charset 字符集
     * @param allowLazyTime 允许的最大阻塞等待时间（毫秒）
     * @return 下一段字符串，或 {@code null}
     */
    String nextString(Charset charset, long allowLazyTime);

    /**
     * 按指定字符集获取下一段字符串
     *
     * <p>
     * 支持跨 ByteBuf 的字符解码（例如 UTF-8 半个字符的情况）
     * </p>
     *
     * @param charset 字符集
     * @return 下一段字符串，或 {@code null}
     */
    String nextString(Charset charset);

    /**
     * 按默认字符集获取下一段字符串
     *
     * <p>
     * 支持跨 ByteBuf 的字符解码（例如 UTF-8 半个字符的情况）
     * </p>
     *
     * @return 下一段字符串，或 {@code null}
     */
    String nextString(long allowLazyTime);

    /**
     * 按默认字符集获取下一段字符串
     *
     * <p>
     * 支持跨 ByteBuf 的字符解码（例如 UTF-8 半个字符的情况）
     * </p>
     *
     * @return 下一段字符串，或 {@code null}
     */
    String nextString();

    /**
     * 获取 HTTP Trailer Headers（用于 chunked 响应）
     *
     * @return trailer headers 的 Future
     */
    Future<HttpHeaders> trailerHeaders();
}
