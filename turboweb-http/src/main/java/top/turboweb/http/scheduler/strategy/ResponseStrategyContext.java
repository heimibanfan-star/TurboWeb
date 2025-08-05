package top.turboweb.http.scheduler.strategy;

import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.response.InternalCallResponse;

import java.util.Map;

/**
 * 响应策略上下文
 */
public class ResponseStrategyContext {

    private final Map<InternalCallResponse.InternalCallType, ResponseStrategy> responseStrategyMap;

    public ResponseStrategyContext(boolean enableLimit) {
        responseStrategyMap = Map.of(
                InternalCallResponse.InternalCallType.SSE, new SseResponseStrategy(),
                InternalCallResponse.InternalCallType.FILE_STREAM, new FileStreamResponseStrategy(),
                InternalCallResponse.InternalCallType.DEFAULT, new DefaultResponseStrategy(),
                InternalCallResponse.InternalCallType.AIO_FILE, new AsyncFileResponseStrategy(),
                InternalCallResponse.InternalCallType.ZERO_COPY, new ZeroCopyResponseStrategy(),
                InternalCallResponse.InternalCallType.REACTOR, new ReactorResponseStrategy(enableLimit)
        );
    }


    /**
     * 选择响应策略
     *
     * @param response 响应
     * @return 响应策略
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
