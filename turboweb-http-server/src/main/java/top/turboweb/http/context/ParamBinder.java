package top.turboweb.http.context;

import io.netty.handler.codec.http.multipart.FileUpload;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * {@code ParamBinder} 定义了 TurboWeb 框架中请求参数的统一访问与封装接口，
 * 用于在控制器层快速提取、绑定、校验 HTTP 请求参数。
 * <p>
 * 它是 {@link HttpContext} 的核心组成部分，负责实现：
 * <ul>
 *     <li>路径参数（Path Variable）的注入与读取</li>
 *     <li>查询参数（Query Parameter）的提取与类型转换</li>
 *     <li>表单与 JSON 参数的自动封装与可选校验</li>
 *     <li>上传文件的访问与管理</li>
 * </ul>
 * <p>
 * 该接口屏蔽了底层 Netty 与请求解析细节，使上层代码可以以统一方式访问参数，
 * 无需关心请求的编码方式（GET/POST/JSON/Form）。
 * </p>
 *
 * <h2>设计原则</h2>
 * <ul>
 *     <li>轻量化：不进行隐式缓存或状态持久，仅在当前请求上下文内有效。</li>
 *     <li>显式校验：仅在调用 <code>loadValid*</code> 系列方法时执行 JSR303 校验。</li>
 *     <li>类型安全：常用基础类型（数值、布尔、日期等）提供直接转换方法。</li>
 *     <li>可组合：可与 {@link HttpContext} 配合，实现注解驱动的参数注入。</li>
 * </ul>
 *
 * <h2>示例用法</h2>
 * <pre>{@code
 * public void handle(HttpContext ctx) {
 *     // 获取查询参数
 *     String keyword = ctx.query("keyword", "default");
 *     int page = ctx.queryInt("page", 1);
 *
 *     // 绑定为对象
 *     SearchRequest req = ctx.loadValidQuery(SearchRequest.class);
 *
 *     // 获取上传文件
 *     FileUpload file = ctx.loadFile("avatar");
 * }
 * }</pre>
 *
 * <p><b>线程安全性：</b> 本接口的实现通常与请求绑定，不可跨请求或多线程共享。</p>
 *
 * @see HttpContext
 * @see io.netty.handler.codec.http.multipart.FileUpload
 * @see top.turboweb.http.context.content.HttpContent
 */
public interface ParamBinder {

    /**
     * 校验对象
     *
     * @param obj 对象
     */
    void validate(Object obj);

    /**
     * 校验对象
     *
     * @param obj 待校验对象
     * @param groups 待校验分组
     */
    void validate(Object obj, Class<?>... groups);

    /**
     * 注入路径参数
     *
     * @param params 路径参数
     */
    void injectPathParam(Map<String, String> params);

    /**
     * 获取路径参数
     *
     * @param name 参数名
     * @return 参数值
     */
    String param(String name);

    Integer paramInt(String name);

    Long paramLong(String name);

    Boolean paramBool(String name);

    Double paramDouble(String name);

    LocalDate paramDate(String name);

    /**
     * 获取查询参数
     *
     * @param name 参数名
     * @return 参数值
     */
    List<String> queries(String name);

    String query(String name);

    String query(String name, String defaultValue);

    List<Long> queriesLong(String name);

    Long queryLong(String name);

    Long queryLong(String name, long defaultValue);

    List<Integer> queriesInt(String name);

    Integer queryInt(String name);

    Integer queryInt(String name, int defaultValue);

    List<Boolean> queriesBool(String name);

    Boolean queryBool(String name);

    Boolean queryBool(String name, Boolean defaultValue);

    List<Double> queriesDouble(String name);

    Double queryDouble(String name);

    Double queryDouble(String name, double defaultValue);

    /**
     * 将查询参数封装为对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadQuery(Class<T> beanType);

    <T> T loadValidQuery(Class<T> beanType);

    /**
     * 将查询参数封装为对象
     *
     * @param beanType 待封装对象类型
     * @param groups 待校验分组
     * @return 封装对象
     */
    <T> T loadValidQuery(Class<T> beanType, Class<?>... groups);

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadForm(Class<T> beanType);

    <T> T loadValidForm(Class<T> beanType);

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 待封装对象类型
     * @param groups 待校验分组
     * @return 封装对象
     */
    <T> T loadValidForm(Class<T> beanType, Class<?>... groups);

    /**
     * 将json参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadJson(Class<T> beanType);

    <T> T loadValidJson(Class<T> beanType);

    /**
     * 将json参数封装成对象
     *
     * @param beanType 待封装对象类型
     * @param groups 待校验分组
     * @return 封装对象
     */
    <T> T loadValidJson(Class<T> beanType, Class<?>... groups);

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
