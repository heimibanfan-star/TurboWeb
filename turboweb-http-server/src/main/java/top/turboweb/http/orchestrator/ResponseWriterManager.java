package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.core.response.ResponseCall;
import top.turboweb.server.runtime.ResourceProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 响应写出器管理器：按响应声明的写出器类型选择写出器并执行写出。
 * <p>
 * 构造期将全部内置 {@link ResponseWriter} 实现按具体类登记到内置表；
 * 自定义写出器经构造器列表传入，按写出器具体类收集为不可变登记表：
 * 系统内置写出器类型拒登，同一写出器类重复登记拒登，列表或元素为 null 拒登。
 * 内置表与自定义表的全部实例在 final 字段赋值之前完成初始化注入，
 * 保证经对象发布可见的写出器均已完成初始化。
 * </p>
 * <p>
 * 写出按三级次序路由：响应实现 {@link ResponseCall} 时按其声明的写出器类型
 * 先查内置表，命中即用；内置表未命中查自定义登记表；
 * 仍未匹配或响应未实现该接口时，落默认写出器 {@link DefaultResponseWriter} 兜底，
 * 确保响应链不会中断。
 * </p>
 *
 * <p><b>特性：</b></p>
 * <ul>
 *     <li>响应经 {@link ResponseCall} 声明写出器类型，内置表按类型直接命中。</li>
 *     <li>默认写出器兜底，避免空指针与未声明响应类型问题。</li>
 *     <li>写出结果以 {@link ResponseWriter.WriteResult} 返回凭据状态与完成凭据，
 *         上游按状态判断即可，无须对凭据做空值检查。</li>
 *     <li>构造期经 initWriter 把资源提供者交给全部写出器，
 *         {@link FileStreamResponseWriter} 等按需从其中取用磁盘IO线程池调度文件分块读取。</li>
 * </ul>
 */
public class ResponseWriterManager {

    /**
     * 内置写出器登记表：写出器具体类到实例，构造期一次登记、不可变。
     */
    private final Map<Class<? extends ResponseWriter>, ResponseWriter> internalResponseWriter;
    /**
     * 自定义写出器登记表：写出器具体类到实例，构造期由传入列表收集为不可变表；
     * 内置声明类型未命中时按该表认领响应写出。
     */
    private final Map<Class<? extends ResponseWriter>, ResponseWriter> customerResponseWriter;
    /** 默认写出器实例，即内置表 {@link DefaultResponseWriter} 类的登记项，所有未匹配路径的共同兜底。 */
    private final ResponseWriter DEFAULT_RESPONSE_WRITER;

    /**
     * 构造响应写出器管理器。
     * <p>
     * 内置表与经校验的自定义不可变表先在写出器初始化完成后，
     * 再统一赋值 final 字段（发布安全：final 字段随对象发布可见时，写出器已初始化完毕）。
     *
     * @param resourceProvider 资源提供者，经 initWriter 交给全部写出器按需取用内置资源
     *                         （磁盘IO线程池供 {@link FileStreamResponseWriter} 调度文件分块读取），不能为null
     * @param customerWriters 自定义写出器列表，按写出器具体类登记为不可变表，不能为null、元素不能为null
     * @throws NullPointerException     资源提供者或列表为null、列表元素为null
     * @throws IllegalArgumentException 列表含系统内置写出器类
     * @throws IllegalStateException    自定义写出器实例已初始化过（同一实例传入过其他管理器），或同一写出器类在列表中出现多次
     * @throws top.turboweb.server.runtime.ResourceException 资源提供者中未登记磁盘IO线程池
     */
    public ResponseWriterManager(ResourceProvider resourceProvider, List<ResponseWriter> customerWriters) {
        Objects.requireNonNull(resourceProvider, "The resource provider must not be null");
        Objects.requireNonNull(customerWriters, "The custom writer list must not be null");
        // 全部初始化（建表、校验、注入资源提供者）先在局部变量上完成，后赋值 final 字段
        Map<Class<? extends ResponseWriter>, ResponseWriter> internalTable = Map.of(
                SseResponseWriter.class, new SseResponseWriter(),
                FileStreamResponseWriter.class, new FileStreamResponseWriter(),
                DefaultResponseWriter.class, new DefaultResponseWriter(),
                ZeroCopyResponseWriter.class, new ZeroCopyResponseWriter(),
                ReactorResponseWriter.class, new ReactorResponseWriter(),
                IgnoredResponseWriter.class, new IgnoredResponseWriter()
        );
        Map<Class<? extends ResponseWriter>, ResponseWriter> customerTable =
                collectCustomerTable(customerWriters, internalTable.keySet());
        Stream.concat(internalTable.values().stream(), customerTable.values().stream())
                .forEach(writer -> writer.initWriter(resourceProvider));
        this.internalResponseWriter = internalTable;
        this.customerResponseWriter = customerTable;
        this.DEFAULT_RESPONSE_WRITER = internalTable.get(DefaultResponseWriter.class);
    }

    /**
     * 把自定义写出器列表按写出器具体类收集为不可变登记表。
     *
     * @param customerWriters 自定义写出器列表，元素不能为null
     * @param builtinTypes    系统内置写出器类集合，命中即拒登
     * @return 写出器具体类到实例的不可变表
     * @throws NullPointerException     列表元素为null
     * @throws IllegalArgumentException 写出器类为系统内置类型
     * @throws IllegalStateException    同一写出器类出现多次
     */
    private static Map<Class<? extends ResponseWriter>, ResponseWriter> collectCustomerTable(
            List<ResponseWriter> customerWriters, Set<Class<? extends ResponseWriter>> builtinTypes) {
        Map<Class<? extends ResponseWriter>, ResponseWriter> table = new LinkedHashMap<>();
        for (ResponseWriter responseWriter : customerWriters) {
            Objects.requireNonNull(responseWriter, "The response writer must not be null");
            Class<? extends ResponseWriter> writerType = responseWriter.getClass();
            if (builtinTypes.contains(writerType)) {
                throw new IllegalArgumentException("Can not register a built-in response writer: " + writerType.getName());
            }
            if (table.putIfAbsent(writerType, responseWriter) != null) {
                throw new IllegalStateException("Duplicate response writer registration: " + writerType.getName());
            }
        }
        return Map.copyOf(table);
    }

    /**
     * 选择写出器并写出响应。
     * <p>
     * 路由次序：响应实现 {@link ResponseCall} 时先按声明类型查内置表，命中即用；
     * 未命中查自定义登记表；仍未匹配落默认写出器兜底。
     * 写出结果（凭据状态与完成凭据）返回给调用方：
     * 可监听时凭据非空、可挂接监听；不可监听时（如忽略写出、写出异常已关闭连接）
     * 凭据为 {@code null}，调用方按状态判断即可，无须对凭据做空值检查。
     *
     * @param response 响应对象（可能实现 {@link ResponseCall} 声明写出器类型）
     * @param session  当前连接的会话上下文
     * @return 写出结果，永不为null
     * @throws ClassCastException 会话不是内部连接会话实现时
     */
    public ResponseWriter.WriteResult write(HttpResponse response, HttpConnection session) {
        ResponseWriter responseWriter = null;
        if (response instanceof ResponseCall responseCall) {
            // 获取响应声明的写出器类型
            Class<? extends ResponseWriter> writerType = responseCall.getWriterType();
            responseWriter = internalResponseWriter.get(writerType);
            if (responseWriter == null) {
                responseWriter = customerResponseWriter.get(writerType);
            }
        }
        if (responseWriter == null) {
            responseWriter = DEFAULT_RESPONSE_WRITER;
        }
        return responseWriter.write(response, (InternalHttpConnection) session);
    }
}
