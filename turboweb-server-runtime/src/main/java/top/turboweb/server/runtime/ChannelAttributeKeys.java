package top.turboweb.server.runtime;

import io.netty.util.AttributeKey;

/**
 * 服务器层与协议层之间经channel附件传递的标记键集合。
 * <p>
 * 各键的存在本身即语义，取值不参与判定；打标方自行负责在正确的时机落标，
 * 标记一经落定通常与连接同寿，无须清除。
 */
public final class ChannelAttributeKeys {

    /**
     * 空闲豁免标记：附件存在本键时，服务器层空闲管理不回收该连接，
     * 连接生命周期由打标方自负。适用于已移交长生命周期协议的连接
     * （升级为websocket、开启SSE等）：此类连接静默期可能远超空闲阈值，
     * 且升级后协议侧自有存活机制。
     * <p>
     * 代价边界：豁免后客户端半开死亡且无业务写出的连接将滞留至OS层回收，
     * 长静默协议侧应自行约定心跳。打标时机须在协议移交完成之后
     * （如websocket握手完成、SSE响应头写出前），不得对普通http连接提前打标。
     */
    public static final AttributeKey<Boolean> IDLE_EXEMPT = AttributeKey.valueOf("turboweb.channel.idle-exempt");

    private ChannelAttributeKeys() {
    }
}
