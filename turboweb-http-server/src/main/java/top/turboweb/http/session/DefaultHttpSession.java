package top.turboweb.http.session;

import top.turboweb.commons.exception.TurboSessionException;

import java.util.UUID;

/**
 * HTTP会话的默认实现类，实现了{@link HttpSession}接口，封装了HTTP场景下的会话管理逻辑。
 * <p>
 * 该类依赖于{@link SessionManager}处理底层的会话存储与属性操作，自身负责sessionId的生成、
 * 会话路径管理及会话有效性检查。支持会话自动创建（当首次操作属性时），并通过UUID保证sessionId的唯一性。
 * </p>
 */
public class DefaultHttpSession implements HttpSession {

    /**
     * 会话管理器实例，负责处理属性的存储、获取、删除等底层操作
     */
    private final SessionManager sessionManager;

    /**
     * 会话的唯一标识，由UUID生成，首次操作属性时自动创建
     */
    private String sessionId;

    /**
     * 生成sessionId时的最大重试次数，避免因并发冲突导致的创建失败
     */
    private final int MAX_RETRY_COUNT = 5;

    /**
     * 会话关联的路径，用于客户端Cookie的路径属性，默认为"/"（根路径）
     */
    private String path = "/";

    /**
     * 标识会话路径是否已更新，用于判断是否需要向客户端同步路径变更
     */
    private boolean pathIsUpdate = false;

    /**
     * 构造函数，初始化会话管理器，会话ID将在首次操作属性时自动生成。
     *
     * @param sessionManager 会话管理器实例，非空
     */
    public DefaultHttpSession(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 构造函数，使用指定的sessionId初始化会话（若会话存在）。
     * <p>
     * 若传入的sessionId对应的会话不存在（通过{@link SessionManager#exist(String)}判断），
     * 则sessionId保持为null，直到首次操作属性时自动生成新的sessionId。
     * </p>
     *
     * @param sessionManager 会话管理器实例，非空
     * @param sessionId      待验证的会话ID，可为null（此时等同于无参构造）
     */
    public DefaultHttpSession(SessionManager sessionManager, String sessionId) {
        this.sessionManager = sessionManager;
        if (sessionId != null && sessionManager.exist(sessionId)) {
            this.sessionId = sessionId;
        }
    }

    /**
     * 向会话中设置属性（无过期时间）。
     * <p>
     * 若sessionId为null（会话未创建），则先调用{@link #checkAndGenerateSession()}自动创建会话。
     * 属性值最终通过{@link SessionManager#setAttr(String, String, Object)}存储到底层容器。
     * </p>
     *
     * @param key   属性的唯一标识，非空字符串
     * @param value 属性的值，可为null
     */
    @Override
    public void setAttr(String key, Object value) {
        checkAndGenerateSession();
        sessionManager.setAttr(sessionId, key, value);
    }

    /**
     * 向会话中设置带过期时间的属性。
     * <p>
     * 若会话未创建，则先自动创建会话。属性的过期时间由底层会话管理器处理，
     * 超过指定时间后属性将不可用。
     * </p>
     *
     * @param key     属性的唯一标识，非空字符串
     * @param value   属性的值，可为null
     * @param timeout 属性的过期时间（单位：毫秒）
     */
    @Override
    public void setAttr(String key, Object value, long timeout) {
        checkAndGenerateSession();
        sessionManager.setAttr(sessionId, key, value, timeout);
    }

    /**
     * 从会话中获取属性值。
     * <p>
     * 若sessionId为null（会话未创建），则直接返回null。否则通过会话管理器获取属性值。
     * </p>
     *
     * @param key 属性的唯一标识，非空字符串
     * @return 属性的值，若会话未创建或属性不存在则返回null
     */
    @Override
    public Object getAttr(String key) {
        if (sessionId == null) {
            return null;
        }
        return sessionManager.getAttr(sessionId, key);
    }

    /**
     * 从会话中获取属性值，并转换为指定类型。
     * <p>
     * 若会话未创建，则返回null。否则通过会话管理器获取并转换属性值 。
     * </p>
     *
     * @param key   属性的唯一标识，非空字符串
     * @param clazz 目标类型的Class对象，非空
     * @param <T>   目标类型的泛型参数
     * @return 转换后的属性值，转化失败抛出异常
     */
    @Override
    public <T> T getAttr(String key, Class<T> clazz) {
        if (sessionId == null) {
            return null;
        }
        return sessionManager.getAttr(sessionId, key, clazz);
    }

    /**
     * 从会话中删除属性。
     * <p>
     * 若sessionId为null，则先自动创建会话（避免误操作），再执行删除。
     * 若会话不存在，删除操作无效果。
     * </p>
     *
     * @param key 属性的唯一标识，非空字符串
     */
    @Override
    public void remAttr(String key) {
        if (sessionId != null) {
            checkAndGenerateSession();
            sessionManager.remAttr(sessionId, key);
        }
    }

    /**
     * 为当前会话续时
     * <p>
     * 若sessionId为null（会话未创建），则该操作无效果。
     * </p>
     */
    @Override
    public void expireAt() {
        if (sessionId != null) {
            sessionManager.expireAt(sessionId);
        }
    }

    /**
     * 检查sessionId是否为空，若为空则生成新的sessionId并创建会话。
     * <p>
     * 生成逻辑：
     * 1. 使用UUID生成不含横线的字符串作为候选sessionId；
     * 2. 尝试通过会话管理器创建会话，若失败（因并发冲突）则重试，最多重试{@link #MAX_RETRY_COUNT}次；
     * 3. 若超过最大重试次数仍失败，则抛出{@link TurboSessionException}。
     * </p>
     *
     * @throws TurboSessionException 当sessionId生成失败时抛出
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

    /**
     * 获取当前会话的sessionId。
     * <p>
     * 若会话未创建（sessionId为null），则返回null。
     * </p>
     *
     * @return 会话的唯一标识，可为null
     */
    @Override
    public String sessionId() {
        return this.sessionId;
    }

    /**
     * 设置会话关联的路径，并标记路径已更新。
     * <p>
     * 路径通常用于客户端Cookie的Path属性，限制Cookie的发送范围。设置后，{@link #pathIsUpdate()}将返回true。
     * </p>
     *
     * @param path 会话路径，非空字符串（建议以"/"开头）
     */
    @Override
    public void setPath(String path) {
        this.path = path;
        this.pathIsUpdate = true;
    }

    /**
     * 判断会话路径是否已更新。
     * <p>
     * 用于服务器判断是否需要在响应中向客户端同步路径变更（如更新Cookie的Path）。
     * 调用{@link #setPath(String)}后，该方法返回true，直到路径被再次设置或会话过期。
     * </p>
     *
     * @return true：路径已更新；false：路径未更新
     */
    @Override
    public boolean pathIsUpdate() {
        return this.pathIsUpdate;
    }

    /**
     * 获取当前会话关联的路径。
     * <p>
     * 默认为"/"，表示根路径，即该会话的Cookie在所有路径下都可见。
     * </p>
     *
     * @return 会话路径，非空字符串
     */
    @Override
    public String getPath() {
        return this.path;
    }
}
