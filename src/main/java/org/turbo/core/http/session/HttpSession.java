package org.turbo.core.http.session;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * http相关的session
 */
public class HttpSession implements Session {

    private final Map<String, SessionAttributeDefinition> attributes = new ConcurrentHashMap<>();
    private long lastUseTime;

    public HttpSession() {
        lastUseTime = System.currentTimeMillis();
    }

    @Override
    public void setAttribute(String key, Object value) {
        SessionAttributeDefinition definition = new SessionAttributeDefinition(value, null);
        attributes.put(key, definition);
        lastUseTime = System.currentTimeMillis();
    }

    @Override
    public void setAttribute(String key, Object value, int timeout) {
        SessionAttributeDefinition definition = new SessionAttributeDefinition(value, System.currentTimeMillis() + timeout);
        attributes.put(key, definition);
        lastUseTime = System.currentTimeMillis();
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
        lastUseTime = System.currentTimeMillis();
    }

    @Override
    public Object getAttribute(String key) {
        lastUseTime = System.currentTimeMillis();
        SessionAttributeDefinition definition = attributes.get(key);
        if (definition == null) {
            return null;
        }
        // 判断是否过期
        if (definition.isTimeout()) {
            return null;
        }
        return definition.getValue();
    }

    @Override
    public boolean isTimeout(long maxNotUseTime) {
        if (maxNotUseTime == -1) {
            return false;
        }
        // 获取当前时间
        long timeMillis = System.currentTimeMillis();
        return timeMillis - lastUseTime > maxNotUseTime;
    }

    @Override
    public Map<String, SessionAttributeDefinition> getAllAttributeDefinitions() {
        return attributes;
    }

    @Override
    public void setUseTime() {
        lastUseTime = System.currentTimeMillis();
    }
}
