package top.turboweb.http.server.handler.manager;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslContext;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.server.handler.ConnectLimiter;
import top.turboweb.http.server.handler.HttpProtocolDispatcher;
import top.turboweb.http.server.handler.RequestSerializerHandler;
import top.turboweb.http.server.handler.WebSocketInitHandler;
import top.turboweb.http.server.handler.group.ConnectLimiterGroup;
import top.turboweb.http.server.handler.group.HttpRequestGroup;
import top.turboweb.http.server.handler.group.NettyHandlerGroup;
import top.turboweb.http.server.handler.group.ProtocolCodecGroup;
import top.turboweb.http.server.handler.group.WebSocketGroup;
import top.turboweb.websocket.handler.WebSocketHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Netty处理器管理器（不可变），管理的是处理器分组而非单个处理器。
 * <p>
 * 每一类处理器归属一个独立的 {@link NettyHandlerGroup} 分组，本管理器按固定装配顺序托管
 * 已配置的分组，组装顺序即分组顺序：
 * <ol>
 *     <li>{@link ConnectLimiterGroup} 限流分组：服务器级单例 {@link ConnectLimiter}，
 *     addFirst置于管道最前，先于ssl与编解码监听连接生命周期事件；</li>
 *     <li>{@link ProtocolCodecGroup} 协议编解码分组：http编解码为http服务器必备环节，
 *     本分组恒定生成——配置过直接使用创建的分组，未配置时使用默认分组（不启用ssl、
 *     聚合器取默认最大内容长度）；HttpServerCodec与HttpObjectAggregator恒每连接新建装配，
 *     启用SSL时SslHandler依据可共享的 {@link SslContext} 每连接新建，为组内唯一可选项；</li>
 *     <li>{@link HttpRequestGroup} http请求分组：请求顺序控制器与http分发器，
 *     {@link RequestSerializerHandler} 持有连接级状态每连接新建（位于http聚合器之后），
 *     {@link HttpProtocolDispatcher} 单例复用装配在其后；</li>
 *     <li>{@link WebSocketGroup} websocket分组：{@link WebSocketInitHandler} 单例复用，
 *     装配在分发器之后、管道最后一位。</li>
 * </ol>
 * 建造者的每个方法对应一个处理器分组：方法只传入该分组的可配置参数并产出分组实例
 * （处理器实例由分组内部创建，分组实例也由方法内部创建，均不对外暴露）；同一分组已经
 * 产出后再次配置抛出 {@link IllegalStateException}。除协议编解码分组恒定生成外，其余分组
 * 未配置时不会生成、不参与装配。装配pipeline只能经由 {@link #configure(ChannelPipeline)} 完成：管理器按分组
 * 顺序逐组调用分组的装配方法，每个分组内部决定本组处理器的装配与复用方式（哪些单例复用、
 * 哪些每连接新建）并按类型去重，可在空管道上直接调用完成全部装配，重复调用不会向pipeline
 * 中重复添加。
 *
 * <pre>{@code
 * NettyHandlerManager handlerManager = NettyHandlerManager.builder()
 *         .enableConnectLimiter(10000, ioThreadNum, cpuNum)
 *         .protocolCodec(sslContext, maxContentLength)      // 不配置则用默认分组（无ssl、默认最大内容长度）
 *         .enableHttpRequest(true, httpScheduler)           // 串行化开关与调度器，调度器null表示不装配分发器
 *         .enableWebSocket("/ws/.*", myWebSocketHandler)
 *         .build();
 * // 在ChannelInitializer中按分组顺序一次性装配全部处理器
 * handlerManager.configure(pipeline);
 * }</pre>
 */
public class NettyHandlerManager {

    /**
     * 已配置处理器的分组列表，建造时按固定装配顺序生成（限流 → 协议编解码 → http请求 → websocket），
     * 未配置的分组不生成（协议编解码分组恒生成），列表不可变
     */
    private final List<NettyHandlerGroup> handlerGroups;

    private NettyHandlerManager(Builder builder) {
        List<NettyHandlerGroup> groups = new ArrayList<>(4);
        // 限流分组：配置过才生成
        if (builder.connectLimiterGroup != null) {
            groups.add(builder.connectLimiterGroup);
        }
        // 协议编解码分组：http编解码必备，恒定生成；配置过直接用创建的分组，未配置用默认分组（无ssl、默认最大内容长度）
        groups.add(builder.protocolCodecGroup != null ? builder.protocolCodecGroup : new ProtocolCodecGroup());
        // http请求分组：配置过才生成
        if (builder.httpRequestGroup != null) {
            groups.add(builder.httpRequestGroup);
        }
        // websocket分组：配置过才生成
        if (builder.webSocketGroup != null) {
            groups.add(builder.webSocketGroup);
        }
        this.handlerGroups = List.copyOf(groups);
    }

    /**
     * 创建处理器管理器构建器，默认所有分组均未配置
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取已配置的处理器分组列表（不可变），顺序即装配顺序：
     * 限流分组 → 协议编解码分组 → http请求分组 → websocket分组，未配置的分组不在列表中
     */
    public List<NettyHandlerGroup> getHandlerGroups() {
        return handlerGroups;
    }

    /**
     * 按分组类型获取已配置的分组，用于查看分组内的装配参数；
     * 分组内部创建的处理器实例不单独暴露，仅经由 {@link #configure(ChannelPipeline)} 装配到管道
     *
     * @param groupType 分组类型，不能为null
     * @return 对应分组，未配置时返回null
     */
    public <T extends NettyHandlerGroup> T getGroup(Class<T> groupType) {
        if (groupType == null) {
            throw new IllegalArgumentException("groupType can not be null");
        }
        for (NettyHandlerGroup group : handlerGroups) {
            if (groupType.isInstance(group)) {
                return groupType.cast(group);
            }
        }
        return null;
    }

    /**
     * 按分组顺序逐组装配已配置的处理器到管道：限流分组addFirst置于最前（单例复用），
     * 协议编解码分组（http编解码与聚合器恒每连接新建装配，ssl仅配置过时）、http请求分组（串行化每连接新建、
     * 分发器单例复用）与websocket分组（初始化handler单例复用）依次addLast；
     * 各分组内部已存在同类型handler时跳过，保证可重复调用
     *
     * @param pipeline netty管道（可在空管道上直接调用，完成全部处理器装配）
     */
    public void configure(ChannelPipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline can not be null");
        }
        for (NettyHandlerGroup group : handlerGroups) {
            group.configure(pipeline);
        }
    }

    /**
     * {@link NettyHandlerManager} 的建造者。
     * 每个方法对应一个处理器分组：传入该分组的可配置参数、内部创建分组实例并记录
     * （分组与处理器实例均由内部分组创建，不对外暴露分组对象）；同一分组已经产出后
     * 再次配置（含websocket两个重载之间）抛出 {@link IllegalStateException}；未配置的分组不生成，
     * 协议编解码分组未配置时构建默认分组。是否配置过某分组只能经由
     * {@link #isConfigured(Class)} 以布尔结果判断（服务器据此补全未配置的分组）。
     */
    public static final class Builder {

        private ConnectLimiterGroup connectLimiterGroup;

        private ProtocolCodecGroup protocolCodecGroup;

        private HttpRequestGroup httpRequestGroup;

        private WebSocketGroup webSocketGroup;

        private Builder() {
        }

        /**
         * 启用限流（一个方法产出一个分组，重复配置抛出异常），
         * 传入可配置参数，内部创建 {@link ConnectLimiterGroup} 限流分组并创建单例 {@link ConnectLimiter}
         * 按io线程数与cpu核数选择计数器实现，装配时addFirst置于管道最前
         *
         * @param maxConnect  最大连接数，必须大于0
         * @param ioThreadNum io工作线程数，必须大于0
         * @param cpuNum      cpu核数，必须大于0
         */
        public Builder enableConnectLimiter(int maxConnect, int ioThreadNum, int cpuNum) {
            if (connectLimiterGroup != null) {
                throw new IllegalStateException("connectLimiter has been enabled, enable it only once");
            }
            this.connectLimiterGroup = new ConnectLimiterGroup(maxConnect, ioThreadNum, cpuNum);
            return this;
        }

        /**
         * 配置协议编解码（一个方法产出一个分组，重复配置抛出异常），
         * 传入可配置参数，内部创建 {@link ProtocolCodecGroup} 协议编解码分组：http协议编解码为服务器
         * 必备环节恒装配，装配时每连接新建 HttpServerCodec 与 HttpObjectAggregator（位于SslHandler之后、
         * http请求分组之前），ssl为组内唯一可选项。不调用本方法使用默认分组（不启用ssl、聚合器取默认最大内容长度）
         *
         * @param sslContext       ssl上下文，可为null表示不启用ssl
         * @param maxContentLength http聚合器最大内容长度，必须大于0
         */
        public Builder protocolCodec(SslContext sslContext, int maxContentLength) {
            if (protocolCodecGroup != null) {
                throw new IllegalStateException("protocolCodec has been configured, configure it only once");
            }
            this.protocolCodecGroup = new ProtocolCodecGroup(sslContext, maxContentLength);
            return this;
        }

        /**
         * 启用http请求处理（一个方法产出一个分组，重复配置抛出异常），
         * 传入可配置参数，内部创建 {@link HttpRequestGroup} http请求分组：串行化开关与
         * {@link top.turboweb.http.server.config.HttpServerConfig#isSerializePerConnection()} 同名；
         * http调度器由分组内部创建单例 {@link HttpProtocolDispatcher}（装配在串行化之后）。
         * 串行化关闭且调度器为null时分组内无任何处理器，构造报错
         *
         * @param serializePerConnection true表示每条连接装配串行化处理器
         * @param httpScheduler          http调度器，可为null表示不装配http分发器
         */
        public Builder enableHttpRequest(boolean serializePerConnection, HttpScheduler httpScheduler) {
            if (httpRequestGroup != null) {
                throw new IllegalStateException("httpRequest has been enabled, enable it only once");
            }
            this.httpRequestGroup = new HttpRequestGroup(serializePerConnection, httpScheduler);
            return this;
        }

        /**
         * 启用websocket（一个方法产出一个分组，与三参重载
         * 互斥、重复配置抛出异常），传入可配置参数，内部创建 {@link WebSocketGroup} websocket分组并创建
         * 单例 {@link WebSocketInitHandler}（装配在分发器之后、管道最后一位），websocket消息默认由虚拟线程处理
         *
         * @param websocketPath    websocket处理的路径表达式，不能为空
         * @param webSocketHandler websocket处理器，不能为null
         */
        public Builder enableWebSocket(String websocketPath, WebSocketHandler webSocketHandler) {
            if (webSocketGroup != null) {
                throw new IllegalStateException("webSocket has been enabled, enable it only once");
            }
            this.webSocketGroup = new WebSocketGroup(websocketPath, webSocketHandler);
            return this;
        }

        /**
         * 启用websocket（一个方法产出一个分组，与两参重载
         * 互斥、重复配置抛出异常），传入可配置参数，内部创建 {@link WebSocketGroup} websocket分组并创建
         * 单例 {@link WebSocketInitHandler}（装配在分发器之后、管道最后一位），websocket消息由工作窃取线程池处理
         *
         * @param websocketPath     websocket处理的路径表达式，不能为空
         * @param webSocketHandler  websocket处理器，不能为null
         * @param forkJoinThreadNum 工作窃取线程池线程数，必须大于0
         */
        public Builder enableWebSocket(String websocketPath, WebSocketHandler webSocketHandler, int forkJoinThreadNum) {
            if (webSocketGroup != null) {
                throw new IllegalStateException("webSocket has been enabled, enable it only once");
            }
            this.webSocketGroup = new WebSocketGroup(websocketPath, webSocketHandler, forkJoinThreadNum);
            return this;
        }

        /**
         * 判断某类分组是否已配置过（建造者内部持有分组实例但不对外暴露，只以布尔结果判断，
         * 服务器据此补全未配置的分组）
         *
         * @param groupType 分组类型，不能为null
         * @return 已配置返回true，未配置返回false
         */
        public boolean isConfigured(Class<? extends NettyHandlerGroup> groupType) {
            if (groupType == null) {
                throw new IllegalArgumentException("groupType can not be null");
            }
            if (groupType == ConnectLimiterGroup.class) {
                return connectLimiterGroup != null;
            }
            if (groupType == ProtocolCodecGroup.class) {
                return protocolCodecGroup != null;
            }
            if (groupType == HttpRequestGroup.class) {
                return httpRequestGroup != null;
            }
            if (groupType == WebSocketGroup.class) {
                return webSocketGroup != null;
            }
            return false;
        }

        /**
         * 构建处理器管理器，按固定顺序生成分组列表：协议编解码分组恒定生成
         * （配置过直接用创建的分组，未配置用默认分组），其余分组配置过才生成
         */
        public NettyHandlerManager build() {
            return new NettyHandlerManager(this);
        }
    }
}
