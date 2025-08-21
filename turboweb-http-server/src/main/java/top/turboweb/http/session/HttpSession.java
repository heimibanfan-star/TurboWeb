package top.turboweb.http.session;

/**
 * session接口
 */
public interface HttpSession extends HttpSessionStore{

    /**
     * 获取sessionId
     * @return sessionId
     */
    String sessionId();

    /**
     * 设置session路径
     *
     * @param path 路径
     */
    void setPath(String path);

    /**
     * session路径是否更新
     * @return true:更新
     */
    boolean pathIsUpdate();

    /**
     * 获取session路径
     * @return session路径
     */
    String getPath();
}
