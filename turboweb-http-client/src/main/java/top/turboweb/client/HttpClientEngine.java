package top.turboweb.client;

import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpRequestMessage;

/**
 * http 客户端引擎的传输契约：字节进、字节出，只认传输是否完成，
 * 不裁状态码——非 2xx 响应同样正常构造消息返回，状态码裁决归上层；
 * 请求体内容的编码（表单、JSON 等）亦归上层，引擎只接收现成的字节数组。
 *
 * <p>聚合请求与流式请求两入口共用一套传输逻辑，
 * {@link #request(HttpRequestMessage)} 由 {@link #stream(HttpRequestMessage, StreamHandler)}
 * 聚合实现：阻塞等待头到达后构造消息、累积分块、终态返回。</p>
 */
public interface HttpClientEngine {

    /**
     * 发起聚合请求：阻塞至传输完成，响应体全量聚合为字节数组；
     * 传输失败抛出异常，非 2xx 响应正常返回。
     *
     * @param request 请求消息，响应体内容以字节数组填充
     * @return 响应消息，响应体为全量聚合的字节数组
     */
    ByteResponseMessage request(HttpRequestMessage<byte[]> request);

    /**
     * 发起流式请求：响应头、响应体分块与终态经 {@link StreamHandler} 依次回调，
     * 回调在引擎的 netty event loop 线程触发，回调内不得阻塞。
     *
     * @param request 请求消息，响应体内容以字节数组填充
     * @param handler 流式响应回调
     * @return 取消句柄，可在任意回调内或任意线程取消本流
     */
    StreamHandle stream(HttpRequestMessage<byte[]> request, StreamHandler handler);
}
