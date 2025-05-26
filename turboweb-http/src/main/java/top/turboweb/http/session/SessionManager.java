package top.turboweb.http.session;

import java.util.Map;

/**
 * session管理器接口
 */
public interface SessionManager {

    /**
     * 从容器中获取session
     * @param sessionId sessionId
     * @return session对象
     */
    Session getSession(String sessionId);

    /**
     * 添加session到容器
     * @param sessionId sessionId
     * @param session session对象
     */
    void addSession(String sessionId, Session session);

    /**
     * 获取容器中所有session
     * @return session对象
     */
    Map<String, Session> getAllSession();

    /**
     * 开启清理session的哨兵
     * @param checkTime 哨兵检查间隔时间
     * @param maxNotUseTime session最大不活跃时间
     * @param checkForSessionNums 每次检查session的数量
     */
    void startSessionGC(long checkTime, long maxNotUseTime, long checkForSessionNums);

    /**
     * 获取session管理器名称
     * @return session管理器名称
     */
    String getSessionManagerName();
}
