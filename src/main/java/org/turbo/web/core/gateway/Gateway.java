package org.turbo.web.core.gateway;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;


/**
 * 网关接口
 */
public interface Gateway {

    /**
     * 添加服务的节点
     *
     * @param prefix 前缀
     * @param urls 路由
     */
    void addServerNode(String prefix, String... urls);

    /**
     * 匹配节点的主机地址
     *
     * @param uri 请求的路径
     * @return 匹配节点
     */
    String matchNode(String uri);

    /**
     * 请求的转发
     *
     * @param url 主机地址
     * @param fullHttpRequest 发送请求
     * @param channel 管道
     */
    void forwardRequest(String url, FullHttpRequest fullHttpRequest, Channel channel);
}
