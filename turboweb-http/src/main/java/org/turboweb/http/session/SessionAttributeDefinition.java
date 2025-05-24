package org.turboweb.http.session;

import java.time.LocalDateTime;

/**
 * session属性的定义信息
 */
public class SessionAttributeDefinition {

    private final Object value;

    // 过期时间
    private final Long timeout;

    public SessionAttributeDefinition(Object value, Long timeout) {
        this.value = value;
        this.timeout = timeout;
    }

    public Object getValue() {
        return value;
    }

    /**
     * 判断是否过期
     *
     * @return 是否过期
     */
    public boolean isTimeout() {
        if (timeout == null) {
            return false;
        }
        long timeMillis = System.currentTimeMillis();
        return timeMillis - timeout > 0;
    }
}
