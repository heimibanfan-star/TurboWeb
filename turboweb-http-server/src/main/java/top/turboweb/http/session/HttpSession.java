package top.turboweb.http.session;

/**
 * HTTP会话的核心接口，继承{@link HttpSessionStore}并扩展了HTTP场景特有的功能。
 * <p>
 * 该接口定义了会话ID管理、路径控制等HTTP相关的操作，是所有HTTP会话实现类的标准契约。
 * 结合{@link HttpSessionStore}提供的属性操作能力，形成完整的HTTP会话管理规范。
 * </p>
 */
public interface HttpSession extends HttpSessionStore{

    /**
     * 获取当前会话的唯一标识sessionId。
     * <p>
     * sessionId由服务器生成，通常通过Cookie或URL重写的方式传递给客户端，
     * 用于在多次请求之间标识同一用户会话。sessionId应具有唯一性和不可预测性，
     * 以防止会话劫持攻击。
     * </p>
     *
     * @return 会话的唯一标识字符串，会话未初始化时可能返回null
     */
    String sessionId();

    /**
     * 设置当前会话关联的路径。
     * <p>
     * 该路径对应HTTP响应中Set-Cookie头的Path属性，用于限制客户端在哪些路径下发送该会话的Cookie。
     * 例如，路径"/user"表示只有访问"/user"及其子路径时，客户端才会携带该Cookie。
     * 默认为"/"，表示所有路径均可见。
     * </p>
     *
     * @param path 会话关联的路径，非空字符串（建议以"/"开头）
     */
    void setPath(String path);

    /**
     * 判断会话路径是否已更新。
     * <p>
     * 用于标识当前会话的路径在本次请求处理过程中是否被修改。服务器可根据该标识决定是否在响应中
     * 重新设置Cookie的Path属性，以同步路径变更到客户端。
     * </p>
     *
     * @return true：路径在本次请求中已更新；false：路径未更新
     */
    boolean pathIsUpdate();

    /**
     * 获取当前会话关联的路径。
     * <p>
     * 若未通过{@link #setPath(String)}设置，则返回默认路径（通常为"/"）。
     * </p>
     *
     * @return 会话关联的路径字符串，非空
     */
    String getPath();
}
