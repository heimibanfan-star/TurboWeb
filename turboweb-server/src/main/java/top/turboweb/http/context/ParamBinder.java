package top.turboweb.http.context;

import io.netty.handler.codec.http.multipart.FileUpload;

import java.util.List;

/**
 * {@code ParamBinder} 定义请求体侧参数的统一访问与封装接口:
 * <ul>
 *     <li>表单与 JSON 参数的自动封装</li>
 *     <li>上传文件的访问与管理</li>
 * </ul>
 * <p>
 * 查询参数与路径参数不归本接口承载:其值是 URI 与路由匹配的产物,由路由装配解码后
 * 随处理器调用传递(承载对象 {@code RequestParameter}),经参数绑定器按注解取数
 * ({@code @QueryParam}/{@code @BeanQuery}/{@code @PathParam}),不经请求上下文中转。
 * </p>
 * <p>
 * 该接口屏蔽底层 Netty 与请求体解码细节,使上层代码以统一方式访问请求体参数。
 * </p>
 *
 * <h2>设计原则</h2>
 * <ul>
 *     <li>轻量化:不进行隐式缓存或状态持久,仅在当前请求上下文内有效。</li>
 *     <li>体侧边界:只承载请求体数据的读取,URI 侧参数按上段通道取用。</li>
 * </ul>
 *
 * <h2>示例用法</h2>
 * <pre>{@code
 * public void handle(HttpContext ctx) {
 *     // 表单绑定为对象
 *     SearchRequest req = ctx.loadForm(SearchRequest.class);
 *
 *     // 获取上传文件
 *     FileUpload file = ctx.loadFile("avatar");
 * }
 * }</pre>
 *
 * <p><b>线程安全性:</b> 本接口的实现与请求绑定,不可跨请求或多线程共享。</p>
 *
 * @see HttpContext
 * @see io.netty.handler.codec.http.multipart.FileUpload
 * @see top.turboweb.http.context.content.HttpContent
 */
public interface ParamBinder {

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadForm(Class<T> beanType);

    /**
     * 将json参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadJson(Class<T> beanType);

    /**
     * 获取文件上传对象
     *
     * @param fileName 文件名
     * @return 文件上传对象
     */
    List<FileUpload> loadFiles(String fileName);

    /**
     * 获取文件上传对象
     *
     * @param fileName 文件名
     * @return 文件上传对象
     */
    FileUpload loadFile(String fileName);
}
