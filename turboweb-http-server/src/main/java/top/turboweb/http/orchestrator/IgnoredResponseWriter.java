package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.core.connect.InternalHttpConnection;

/**
 * 响应忽略写出器。
 * <p>
 * 当某个请求的响应无需发送到客户端，或者上层逻辑已自行处理响应，可使用该写出器作为空操作。
 * </p>
 *
 * <p>
 * 此写出器不会执行任何写入或刷新操作，仅返回 {@code null}，表示该响应已被忽略，
 * 由上层调用者根据返回值决定是否继续处理。
 * </p>
 */
public class IgnoredResponseWriter extends AbstractResponseWriter {

    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
        return null;
    }
}
