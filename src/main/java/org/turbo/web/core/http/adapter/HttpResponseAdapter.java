package org.turbo.web.core.http.adapter;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import org.turbo.web.core.connect.ConnectSession;

/**
 * http响应的适配器
 */
public interface HttpResponseAdapter {

	/**
	 * 将响应对象适配为netty的响应对象
	 *
	 * @param response 响应对象
	 * @param session  连接会话
	 * @return channelFuture
	 */
	ChannelFuture writeHttpResponse(HttpResponse response, ConnectSession session);

}
