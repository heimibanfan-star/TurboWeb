package top.turboweb.http.session;

/**
 * 禁用session的黑洞Session管理器
 */
public class BackHoleSessionManager implements SessionManager{
    @Override
    public void setAttr(String sessionId, String key, Object value) {
    }

    @Override
    public void setAttr(String sessionId, String key, Object value, long timeout) {
    }

    @Override
    public Object getAttr(String sessionId, String key) {
        return null;
    }

    @Override
    public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
        return null;
    }

    @Override
    public void remAttr(String sessionId, String key) {
    }

    @Override
    public boolean exist(String sessionId) {
        return false;
    }

    @Override
    public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {

    }

    @Override
    public String sessionManagerName() {
        return "backHole session manager";
    }

    @Override
    public void expireAt(String sessionId) {
    }
}