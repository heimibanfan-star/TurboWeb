package top.turboweb.client.engine;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * 响应块
 */
public record ResponseChunk(HttpResponseStatus status, HttpHeaders headers, ByteBuf chunk) {
}
