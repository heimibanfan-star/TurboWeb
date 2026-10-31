package top.turboweb.http.context;

/**
 * 可关闭的请求体内容：持有需归还资源（如解码器、直接内存、磁盘临时文件）的内容实现选用本接口，
 * 创建方（请求编排器）在请求处理结束后经 {@link AutoCloseable#close()} 统一关闭释放；
 * 无此类资源的内容实现不实现本接口，不背负关闭义务。
 */
public interface AutoCloseContent extends AutoCloseable {
}
