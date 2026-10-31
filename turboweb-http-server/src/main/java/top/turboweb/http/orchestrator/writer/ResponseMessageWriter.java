package top.turboweb.http.orchestrator.writer;

import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.orchestrator.ExceptionMessageHelper;
import top.turboweb.server.runtime.ResourceProvider;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * 响应消息写出器：按消息类型与订阅声明选择写出方，
 * 把一条响应消息异步写出到一条连接上，写出结果经 {@link MessageWriteHandler.Callback} 上报。
 * <p>
 * 选择按 Content-Type 门禁与三个区间次序路由：
 * 门禁为消息头完备性——响应消息未携带 {@code Content-Type} 时等同找不到处理器，降级写出报错页；
 * 区间一为字节消息直认——{@link ByteResponseMessage} 为 final 类，类型按字面判定，
 * 命中即由本写出器的字节写出方法写出；
 * 区间二为订阅声明匹配——消息实现 {@link WriteHandlerSubscribe} 且声明了订阅者类型时，
 * 先查内置策略组，未命中再查自定义策略组；
 * 区间三为降级写出——其余消息（未实现订阅声明、未声明订阅者类型，
 * 或声明了写出职责但两级策略组均未登记对应处理器）一律等同找不到处理器，
 * 生成一条携带 500 HTML 报错页的 {@link ByteResponseMessage} 交字节写出方法写出，
 * 保证响应链不会中断；降级消息继承原响应的非实体消息头（如 CORS 声明），
 * 描述原消息体的实体头（内容类型、长度、编码与传输编码）不继承。
 * </p>
 * <p>
 * 构造期把传入的自定义处理器按具体类收集为不可变登记表：
 * 内置处理器类拒登，同一处理器类重复登记拒登，列表或元素为 null 拒登。
 * 内置策略组与自定义策略组的全部处理器在 final 字段赋值之前完成初始化注入，
 * 保证经对象发布可见的处理器均已完成初始化。
 * </p>
 */
public class ResponseMessageWriter {

    /** Content-Length 头名 */
    private static final String CONTENT_LENGTH = "Content-Length";
    /** Content-Type 头名 */
    private static final String CONTENT_TYPE = "Content-Type";
    /** 实体头名集合：描述原消息体的头，降级报错页更换消息体后不再成立，不继承，头名匹配大小写不敏感 */
    private static final List<String> ENTITY_HEADERS =
            List.of("Content-Type", "Content-Length", "Content-Encoding", "Transfer-Encoding");

    /**
     * 内置策略组：订阅者类型到处理器实例，构造期一次登记、不可变。
     */
    private final Map<Class<? extends MessageWriteHandler>, MessageWriteHandler> builtinHandlers;
    /**
     * 自定义策略组：订阅者类型到处理器实例，构造期由传入列表收集为不可变表；
     * 内置策略组未命中时按该表认领消息写出。
     */
    private final Map<Class<? extends MessageWriteHandler>, MessageWriteHandler> customerHandlers;

    /**
     * 构造响应消息写出器。
     * <p>
     * 自定义表与处理器初始化先在局部变量上完成，再统一赋值 final 字段
     * （发布安全：final 字段随对象发布可见时，处理器已初始化完毕）。
     *
     * @param resourceProvider 资源提供者，经 init 交给全部处理器按需取用内置资源，不能为null
     * @param customerHandlers 自定义处理器列表，按处理器具体类登记为不可变表，
     *                         不能为null、元素不能为null
     * @throws NullPointerException     资源提供者或列表为null、列表元素为null
     * @throws IllegalArgumentException 列表含内置处理器类
     * @throws IllegalStateException    同一处理器类在列表中出现多次
     */
    public ResponseMessageWriter(ResourceProvider resourceProvider, List<MessageWriteHandler> customerHandlers) {
        Objects.requireNonNull(resourceProvider, "The resource provider must not be null");
        Objects.requireNonNull(customerHandlers, "The custom handler list must not be null");
        // 全部初始化（建表、校验、注入资源提供者）先在局部变量上完成，后赋值 final 字段
        Map<Class<? extends MessageWriteHandler>, MessageWriteHandler> builtinTable = Map.of();
        Map<Class<? extends MessageWriteHandler>, MessageWriteHandler> customerTable =
                collectCustomerTable(customerHandlers, builtinTable.keySet());
        Stream.concat(builtinTable.values().stream(), customerTable.values().stream())
                .forEach(handler -> handler.init(resourceProvider));
        this.builtinHandlers = builtinTable;
        this.customerHandlers = customerTable;
    }

    /**
     * 把自定义处理器列表按处理器具体类收集为不可变登记表。
     *
     * @param customerHandlers 自定义处理器列表，元素不能为null
     * @param builtinTypes     内置处理器类集合，命中即拒登
     * @return 处理器具体类到实例的不可变表
     * @throws NullPointerException     列表元素为null
     * @throws IllegalArgumentException 处理器类为内置类型
     * @throws IllegalStateException    同一处理器类出现多次
     */
    private static Map<Class<? extends MessageWriteHandler>, MessageWriteHandler> collectCustomerTable(
            List<MessageWriteHandler> customerHandlers, Set<Class<? extends MessageWriteHandler>> builtinTypes) {
        Map<Class<? extends MessageWriteHandler>, MessageWriteHandler> table = new LinkedHashMap<>();
        for (MessageWriteHandler handler : customerHandlers) {
            Objects.requireNonNull(handler, "The message write handler must not be null");
            Class<? extends MessageWriteHandler> handlerType = handler.getClass();
            if (builtinTypes.contains(handlerType)) {
                throw new IllegalArgumentException("Can not register a built-in message write handler: " + handlerType.getName());
            }
            if (table.putIfAbsent(handlerType, handler) != null) {
                throw new IllegalStateException("Duplicate message write handler registration: " + handlerType.getName());
            }
        }
        return Map.copyOf(table);
    }

    /**
     * 选择写出方并写出响应消息。
     * <p>
     * 路由次序：字节消息直认落区间一，由字节写出方法写出；
     * 实现 {@link WriteHandlerSubscribe} 且声明了订阅者类型的消息按声明类型
     * 先查内置策略组、未命中查自定义策略组，落区间二；
     * 其余消息一律等同找不到处理器，落区间三降级写出报错页。
     * </p>
     *
     * @param connection 目标连接，提供写出通道
     * @param message    待写出的响应消息
     * @param callback   写出回调，报告本轮写出的唯一结果，不能为 {@code null}
     */
    public void write(HttpConnection connection, HttpResponseMessage<?> message, MessageWriteHandler.Callback callback) {
        if (!message.containsHeader(CONTENT_TYPE)) {
            // 响应未携带 Content-Type：等同找不到处理器，降级写出报错页
            writeByteMessage(connection, errorPageFallback(message, ExceptionMessageHelper.missingContentTypeMessage()), callback);
            return;
        }
        if (message instanceof ByteResponseMessage byteMessage) {
            writeByteMessage(connection, byteMessage, callback);
            return;
        }
        MessageWriteHandler handler = null;
        if (message instanceof WriteHandlerSubscribe subscribe && subscribe.subscriber() != null) {
            // 获取消息声明的订阅者类型
            Class<? extends MessageWriteHandler> subscriberType = subscribe.subscriber();
            handler = builtinHandlers.get(subscriberType);
            if (handler == null) {
                handler = customerHandlers.get(subscriberType);
            }
        }
        if (handler == null) {
            // 其余消息一律等同找不到处理器：降级写出报错页
            writeByteMessage(connection, errorPageFallback(message, ExceptionMessageHelper.unsupportedWriteHandlerMessage()), callback);
            return;
        }
        handler.write(connection, message, callback);
    }

    /**
     * 字节写出方法：把字节响应消息持有的字节数组按入参原样写出，
     * 经连接的消息头、消息体与结束标记三步写出，写出结果经回调恰好上报一次。
     * <p>
     * 消息头中未携带 {@code Content-Length} 时按消息体长度补齐，已携带时不覆写；
     * 消息体为空数组时跳过消息体写出，仍写出结束标记；
     * 异步写出失败与写出通道同步抛错经回调以 FINISHED 上报，失败链不再写结束标记。
     * </p>
     *
     * @param connection 目标连接，提供写出通道
     * @param message    待写出的字节响应消息
     * @param callback   写出回调，报告本轮写出的唯一结果
     */
    private void writeByteMessage(HttpConnection connection, ByteResponseMessage message, MessageWriteHandler.Callback callback) {
        byte[] content = Optional.ofNullable(message.content()).orElse(new byte[0]);
        if (!message.containsHeader(CONTENT_LENGTH)) {
            message.setHeader(CONTENT_LENGTH, String.valueOf(content.length));
        }
        try {
            connection.writeHeaders(message.statusLine(), message.headers())
                    .thenCompose(v -> content.length == 0
                            ? CompletableFuture.<Void>completedFuture(null)
                            : connection.write(content))
                    .thenCompose(v -> connection.finishWrite())
                    .whenComplete((v, e) -> callback.onFinally(
                            e instanceof CompletionException completion ? completion.getCause() : e,
                            MessageWriteHandler.CallbackType.FINISHED));
        } catch (Throwable e) {
            callback.onFinally(e, MessageWriteHandler.CallbackType.FINISHED);
        }
    }

    /**
     * 生成区间三降级用的字节响应消息：携带给定 500 HTML 报错页文本与配套响应头。
     * <p>
     * 原响应的消息头继承到降级消息（如 CORS 声明、Set-Cookie 需对报错页继续生效），
     * 描述原消息体的实体头除外——内容类型、长度、编码与传输编码不再描述报错页，
     * 内容类型与长度由降级消息按自身正文重设。
     * </p>
     *
     * @param original 触发降级的原响应消息
     * @param page     报错页文本，由异常信息 Helper 统一承载
     * @return 降级报错页字节响应消息
     */
    private ByteResponseMessage errorPageFallback(HttpResponseMessage<?> original, String page) {
        ByteResponseMessage fallback = new ByteResponseMessage(
                StatusLine.INTERNAL_SERVER_ERROR, page.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, List<String>> entry : original.headers().entrySet()) {
            if (isEntityHeader(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                fallback.addHeader(entry.getKey(), value);
            }
        }
        fallback.setHeader(CONTENT_TYPE, "text/html;charset=utf-8");
        return fallback;
    }

    /**
     * 判断头名是否为实体头：实体头只对原消息体成立，降级报错页更换了消息体，此类头不继承。
     *
     * @param name 头名，大小写不敏感
     * @return 是实体头返回 true
     */
    private static boolean isEntityHeader(String name) {
        return ENTITY_HEADERS.stream().anyMatch(entity -> entity.equalsIgnoreCase(name));
    }
}
