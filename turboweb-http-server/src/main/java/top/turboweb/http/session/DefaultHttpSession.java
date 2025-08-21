package top.turboweb.http.session;

import top.turboweb.commons.exception.TurboSessionException;

import java.util.UUID;

/**
 * http相关的session
 */
public class DefaultHttpSession implements HttpSession {

    private final SessionManager sessionManager;
    private String sessionId;
    private final int MAX_RETRY_COUNT = 5;
    private String path = "/";
    private boolean pathIsUpdate = false;

    public DefaultHttpSession(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public DefaultHttpSession(SessionManager sessionManager, String sessionId) {
        this.sessionManager = sessionManager;
        if (sessionId != null && sessionManager.exist(sessionId)) {
            this.sessionId = sessionId;
        }
    }

    @Override
    public void setAttr(String key, Object value) {
        checkAndGenerateSession();
        sessionManager.setAttr(sessionId, key, value);
    }

    @Override
    public void setAttr(String key, Object value, long timeout) {
        checkAndGenerateSession();
        sessionManager.setAttr(sessionId, key, value, timeout);
    }

    @Override
    public Object getAttr(String key) {
        if (sessionId == null) {
            return null;
        }
        return sessionManager.getAttr(sessionId, key);
    }

    @Override
    public <T> T getAttr(String key, Class<T> clazz) {
        if (sessionId == null) {
            return null;
        }
        return sessionManager.getAttr(sessionId, key, clazz);
    }

    @Override
    public void remAttr(String key) {
        if (sessionId != null) {
            checkAndGenerateSession();
            sessionManager.remAttr(sessionId, key);
        }
    }

    @Override
    public void expireAt() {
        if (sessionId != null) {
            sessionManager.expireAt(sessionId);
        }
    }

    /**
     * 检查sessionId是否为空，为空则生成一个sessionId
     */
    private void checkAndGenerateSession() {
        if (this.sessionId == null) {
            String sessionId = UUID.randomUUID().toString().replace("-", "");
            int count = 0;
            while (count < MAX_RETRY_COUNT && !sessionManager.createSessionMap(sessionId)) {
                sessionId = UUID.randomUUID().toString().replace("-", "");
                count++;
            }
            if (count >= MAX_RETRY_COUNT) {
                throw new TurboSessionException("sessionId generate error");
            }
            this.sessionId = sessionId;
        }
    }

    @Override
    public String sessionId() {
        return this.sessionId;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
        this.pathIsUpdate = true;
    }

    @Override
    public boolean pathIsUpdate() {
        return this.pathIsUpdate;
    }

    @Override
    public String getPath() {
        return this.path;
    }
}
