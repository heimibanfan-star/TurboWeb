package top.turboweb.http.response.handler;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import top.turboweb.http.connect.ConnectSession;

/**
 * http响应的处理器
 */
public interface HttpResponseHandler {

	/**
	 * 将响应对象适配为netty的响应对象
	 *
	 * @param response 响应对象
	 * @param session  连接会话
	 * @return channelFuture
	 */
	ChannelFuture writeHttpResponse(HttpResponse response, ConnectSession session);

}
