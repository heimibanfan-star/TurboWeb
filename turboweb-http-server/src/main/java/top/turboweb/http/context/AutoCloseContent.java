package top.turboweb.http.context;

import top.turboweb.http.core.Content;

/**
 * 可关闭的请求体内容：组合 {@link Content} 与 {@link AutoCloseable} 的公共子类型，
 * 创建方（请求编排器）在请求处理结束后经 {@link AutoCloseable#close()} 统一关闭释放。
 * 实现类仅在持有需归还的资源（如解码器、直接内存、磁盘临时文件）时选用本接口，
 * 无此类资源的实现只实现 {@link Content}，不背负关闭义务。
 */
public interface AutoCloseContent extends Content, AutoCloseable {
}
