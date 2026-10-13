package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.ResponseCall;

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
 * 内置表与自定义表的全部实例在 final 字段赋值之前完成磁盘IO线程池注入，
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
 *     <li>磁盘IO线程池由构造器注入，构造期经 injectThreadPool 交给
 *         {@link FileStreamResponseWriter} 等写出器调度文件分块读取。</li>
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
     * 内置表与经校验的自定义不可变表先在线程池注入完成后，
     * 再统一赋值 final 字段（发布安全：final 字段随对象发布可见时，写出器已初始化完毕）。
     *
     * @param threadPool      磁盘IO线程池，注入 {@link FileStreamResponseWriter} 用于调度文件分块读取，不能为null
     * @param customerWriters 自定义写出器列表，按写出器具体类登记为不可变表，不能为null、元素不能为null
     * @throws NullPointerException     线程池或列表为null、列表元素为null
     * @throws IllegalArgumentException 列表含系统内置写出器类，或同一写出器类出现多次
     */
    public ResponseWriterManager(ThreadPool threadPool, List<ResponseWriter> customerWriters) {
        Objects.requireNonNull(threadPool, "磁盘IO线程池不能为null");
        Objects.requireNonNull(customerWriters, "自定义写出器列表不能为null");
        // 全部初始化（建表、校验、注入线程池）先在局部变量上完成，后赋值 final 字段
        Map<Class<? extends ResponseWriter>, ResponseWriter> internalTable = Map.of(
                SseResponseWriter.class, new SseResponseWriter(),
                FileStreamResponseWriter.class, new FileStreamResponseWriter(),
                DefaultResponseWriter.class, new DefaultResponseWriter(),
                AsyncFileResponseWriter.class, new AsyncFileResponseWriter(),
                ZeroCopyResponseWriter.class, new ZeroCopyResponseWriter(),
                ReactorResponseWriter.class, new ReactorResponseWriter(),
                IgnoredResponseWriter.class, new IgnoredResponseWriter()
        );
        Map<Class<? extends ResponseWriter>, ResponseWriter> customerTable =
                collectCustomerTable(customerWriters, internalTable.keySet());
        Stream.concat(internalTable.values().stream(), customerTable.values().stream())
                .forEach(writer -> writer.injectThreadPool(threadPool));
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
     * @throws IllegalArgumentException 写出器类为系统内置类型，或同一写出器类出现多次
     */
    private static Map<Class<? extends ResponseWriter>, ResponseWriter> collectCustomerTable(
            List<ResponseWriter> customerWriters, Set<Class<? extends ResponseWriter>> builtinTypes) {
        Map<Class<? extends ResponseWriter>, ResponseWriter> table = new LinkedHashMap<>();
        for (ResponseWriter responseWriter : customerWriters) {
            Objects.requireNonNull(responseWriter, "响应写出器不能为null");
            Class<? extends ResponseWriter> writerType = responseWriter.getClass();
            if (builtinTypes.contains(writerType)) {
                throw new IllegalArgumentException("不能注册系统内置的响应写出器:" + writerType.getName());
            }
            if (table.putIfAbsent(writerType, responseWriter) != null) {
                throw new IllegalArgumentException("响应写出器不能重复注册:" + writerType.getName());
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
    public ResponseWriter.WriteResult write(HttpResponse response, ConnectSession session) {
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
        return responseWriter.write(response, (InternalConnectSession) session);
    }
}
