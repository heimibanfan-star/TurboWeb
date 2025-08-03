package top.turboweb.http.response;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * 进行框架内部功能的调用
 * 继承该接口的类，
 */
public interface InternalCallResponse {

    /**
     * 内部调用的类型
     */
    enum InternalCallType {
        // 零拷贝
        ZERO_COPY,
        // 用于调用内部的文件流
        FILE_STREAM,
        // 基于AIO的文件响应
        AIO_FILE,
        // SSE
        SSE,
        // 默认的策略
        DEFAULT,
        // 普通的流式调用
        STREAM
    }

    /**
     * 获取内部调用的类型
     * @return 内部调用的类型
     */
    InternalCallType getType();
}
