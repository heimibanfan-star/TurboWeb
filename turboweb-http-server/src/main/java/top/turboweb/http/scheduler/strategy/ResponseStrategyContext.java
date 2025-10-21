package top.turboweb.http.scheduler.strategy;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.response.InternalCallResponse;

import java.util.Map;

/**
 * <p><b>响应策略上下文（Response Strategy Context）。</b></p>
 *
 * <p>
 * 该类负责在不同响应类型之间进行策略选择与分发，是 TurboWeb 响应处理阶段的核心调度组件。
 * 每种响应类型（如流式文件、SSE、Reactor 等）均对应独立的 {@link ResponseStrategy} 实现，
 * 用于处理特定类型的响应输出逻辑。
 * </p>
 *
 * <p>
 * 当所有策略均未匹配时，将自动回退到 {@code DEFAULT} 策略，确保响应链不会中断。
 * </p>
 *
 * <p><b>特性：</b></p>
 * <ul>
 *     <li>自动匹配 {@link InternalCallResponse.InternalCallType} 类型到对应策略。</li>
 *     <li>默认策略兜底，避免空指针与未定义响应类型问题。</li>
 *     <li>支持启用响应限流（仅影响 {@link ReactorResponseStrategy}）。</li>
 * </ul>
 */
public class ResponseStrategyContext {

    private final Map<InternalCallResponse.InternalCallType, ResponseStrategy> responseStrategyMap;

    /**
     * 构造响应策略上下文。
     * <p>
     * 初始化时将所有内置响应类型注册到对应策略实现中。
     * </p>
     *
     * @param enableLimit 是否启用响应限流（仅影响 Reactor 响应类型）
     */
    public ResponseStrategyContext(boolean enableLimit) {
        responseStrategyMap = Map.of(
                InternalCallResponse.InternalCallType.SSE, new SseResponseStrategy(),
                InternalCallResponse.InternalCallType.FILE_STREAM, new FileStreamResponseStrategy(),
                InternalCallResponse.InternalCallType.DEFAULT, new DefaultResponseStrategy(),
                InternalCallResponse.InternalCallType.AIO_FILE, new AsyncFileResponseStrategy(),
                InternalCallResponse.InternalCallType.ZERO_COPY, new ZeroCopyResponseStrategy(),
                InternalCallResponse.InternalCallType.REACTOR, new ReactorResponseStrategy(enableLimit),
                InternalCallResponse.InternalCallType.IGNORED, new IgnoredResponseStrategy()
        );
    }


    /**
     * 根据响应对象选择对应的策略实现。
     * <p>
     * 若响应类型为 {@link InternalCallResponse}，则根据其内部枚举 {@link InternalCallResponse.InternalCallType}
     * 匹配到对应策略；否则，自动回退到默认策略 {@link DefaultResponseStrategy}。
     * </p>
     *
     * @param response 响应对象（可能为 {@link InternalCallResponse} 或标准 {@link HttpResponse}）
     * @return 匹配的响应策略，若未匹配到则返回默认策略
     */
    public ResponseStrategy chooseStrategy(HttpResponse response) {
        if (response instanceof InternalCallResponse internalCallResponse) {
            // 获取响应类型
            InternalCallResponse.InternalCallType type = internalCallResponse.getType();
            ResponseStrategy responseStrategy = responseStrategyMap.get(type);
            if (responseStrategy == null) {
                responseStrategy = responseStrategyMap.get(InternalCallResponse.InternalCallType.DEFAULT);
            }
            return responseStrategy;
        } else {
            return responseStrategyMap.get(InternalCallResponse.InternalCallType.DEFAULT);
        }
    }
}
