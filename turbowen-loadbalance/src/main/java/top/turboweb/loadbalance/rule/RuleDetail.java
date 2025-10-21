package top.turboweb.loadbalance.rule;

/**
 * 路由规则详情。
 *
 * <p>用于描述某条路径匹配规则对应的服务信息，包括服务名称、是否为本地服务、
 * 协议类型、重写规则和附加路径等。
 */
public record RuleDetail(
        // 服务名称
        String serviceName,
        // 重写正则
        String rewriteRegex,
        // 重写目标
        String rewriteTarget,
        // 是否是本地节点
        boolean local,
        // 协议
        Protocol protocol,
        // 附加路径
        String extPath
) {

    /**
     * 协议类型枚举。
     *
     * <p>支持 HTTP、HTTPS、WebSocket 等协议。
     */
    public enum Protocol {
        HTTP("http"),
        HTTPS("https"),
        WEBSOCKET("ws"),
        WEBSOCKETS("wss"),
        ;
        private final String protocol;

        /**
         * 构造函数
         *
         * @param protocol 协议
         */
        Protocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 获取协议
         *
         * @return 协议
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * 获取协议
         *
         * @param protocol 协议
         * @return 协议
         */
        public static Protocol getProtocol(String protocol) {
            for (Protocol value : values()) {
                if (value.protocol.equals(protocol)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Not a valid protocol:" + protocol);
        }
    }
}
