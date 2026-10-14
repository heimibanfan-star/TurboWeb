package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponse;

import java.nio.charset.Charset;

/**
 * 结果转化器：尝试把单个业务返回值转化为 HTTP 响应。
 * <p>
 * 多个转化器按序组成转化链，每个环节只认领自己支持的返回值类型：
 * 认领时以 {@link Status#FINISHED} 携带转化出的响应并终止链的传递，
 * 不认领时以 {@link Status#CONTINUE} 放行给下一环节；
 * 链尾兜底环节恒认领，保证任意输入都有确定走向。
 * </p>
 */
@FunctionalInterface
public interface ResultConverter {

    /** 单次转化的走向：放行下一环节，或认领并产出响应。 */
    enum Status {
        /** 本环节不认领该输入，转化链继续下一环节。 */
        CONTINUE,
        /** 本环节认领该输入并完成转化，随结果携带响应。 */
        FINISHED
    }

    /**
     * 转化结果：走向 {@code status} 与响应 {@code httpResponse}。
     * <p>
     * 不变式：FINISHED 时响应非空，CONTINUE 时无响应；
     * 实例经静态工厂 {@link #shouldContinue()} 与 {@link #finished(HttpResponse)} 构造。
     * </p>
     */
    record Result(Status status, HttpResponse httpResponse) {

        /**
         * 不认领本次输入：无响应产出，交由转化链下一环节处理。
         *
         * @return CONTINUE 走向的结果
         */
        public static Result shouldContinue() {
            return new Result(Status.CONTINUE, null);
        }

        /**
         * 认领并完成转化。
         *
         * @param httpResponse 转化出的响应
         * @return FINISHED 走向且携带该响应的结果
         */
        public static Result finished(HttpResponse httpResponse) {
            return new Result(Status.FINISHED, httpResponse);
        }
    }

    /**
     * 尝试转化一个业务返回值。
     *
     * @param result  业务返回值，可能为null
     * @param charset 装配期判定的缺省字符集，
     *                用于响应文本编码、文件名编码与缺省内容类型补码
     * @return 转化结果：认领时携响应，不认领时放行
     */
    Result convert(Object result, Charset charset);

}
