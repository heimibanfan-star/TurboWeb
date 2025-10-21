package top.turboweb.http.session;

/**
 * 黑洞会话管理器，是{@link SessionManager}接口的空实现，用于完全禁用会话功能的场景。
 * <p>
 * 该实现中所有方法均不执行实际操作：属性设置/删除无效果、属性获取返回null、会话存在性判断返回false、
 * 垃圾回收机制不运行。适用于不需要会话管理的场景（如无状态API服务），可作为会话功能的"开关"，
 * 避免因移除会话相关代码导致的编译错误。
 * </p>
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
    public boolean createSessionMap(String sessionId) {
        return true;
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