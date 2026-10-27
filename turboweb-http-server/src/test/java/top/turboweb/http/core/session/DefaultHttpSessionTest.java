package top.turboweb.http.core.session;
import top.turboweb.http.session.SessionManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.HttpSessionException;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultHttpSession} 的会话懒创建、读写转发、路径标志与重试耗尽。
 *
 * <p>spec §5 的“`Set-Cookie` 两条触发分支”在会话侧就是这里的两个可断言点：
 * 新会话（`sessionId` 由 null 变非 null）与 `pathIsUpdate()`。写响应头的分支属 processor，不在此处。
 */
class DefaultHttpSessionTest {

    @Test
    @DisplayName("首次写属性才创建会话，属性落到底层管理器")
    void firstAttributeWriteCreatesSessionAndStoresValue() {
        MemorySessionManager manager = new MemorySessionManager();
        DefaultHttpSession session = new DefaultHttpSession(manager);

        assertNull(session.sessionId(), "未写属性前会话未创建");

        session.setAttr("name", "tom");

        String id = session.sessionId();
        assertNotNull(id, "首次写属性应生成 sessionId");
        assertEquals(32, id.length(), "sessionId 由去掉横线的 UUID 生成，恒为 32 位十六进制");
        assertEquals("tom", session.getAttr("name"));
        assertEquals("tom", session.getAttr("name", String.class));
        assertEquals("tom", manager.getAttr(id, "name"), "属性确实落到注入的管理器");
        assertTrue(manager.exist(id));
    }

    @Test
    @DisplayName("会话未创建时读取返回 null，且不触发创建")
    void readsBeforeSessionCreationReturnNull() {
        DefaultHttpSession session = new DefaultHttpSession(new MemorySessionManager());

        assertNull(session.getAttr("k"));
        assertNull(session.getAttr("k", String.class));
        assertNull(session.sessionId(), "只读操作不得创建会话");
    }

    @Test
    @DisplayName("sessionId 为 null 时 remAttr 不创建会话（与 setAttr 相反）")
    void remAttrBeforeSessionCreationDoesNotCreateOne() {
        DefaultHttpSession session = new DefaultHttpSession(new MemorySessionManager());

        session.remAttr("k");

        assertNull(session.sessionId(), "remAttr 的守卫让它不会像 setAttr 那样懒创建会话");
    }

    @Test
    @DisplayName("remAttr 删除已创建会话里的属性")
    void remAttrRemovesStoredAttribute() {
        DefaultHttpSession session = new DefaultHttpSession(new MemorySessionManager());
        session.setAttr("k", "v");

        session.remAttr("k");

        assertNull(session.getAttr("k"));
    }

    @Test
    @DisplayName("setAttr 带过期时间：负 timeout 立即使属性失效")
    void setAttrWithTimeoutExpiresImmediatelyForNegativeTimeout() {
        DefaultHttpSession session = new DefaultHttpSession(new MemorySessionManager());

        session.setAttr("temp", "v", -1L);

        assertNull(session.getAttr("temp"), "负过期时间 = 立即失效，不依赖时钟推进");
    }

    @Test
    @DisplayName("expireAt 只在会话已创建时向下游续时")
    void expireAtDelegatesOnlyWhenSessionExists() {
        StubSessionManager manager = new StubSessionManager();
        DefaultHttpSession session = new DefaultHttpSession(manager);

        session.expireAt();
        assertEquals(0, manager.expireAtCalls, "无会话时不向管理器续时");

        session.setAttr("k", "v");           // 桩的 createSessionMap 返回 true → 会话被创建
        session.expireAt();
        assertEquals(1, manager.expireAtCalls, "有会话时续时一次");
        assertEquals(session.sessionId(), manager.lastExpireAtId, "续时必须带上当前 sessionId");
    }

    @Test
    @DisplayName("仅当指定 sessionId 的会话存在时才被采用")
    void constructorAdoptsOnlyExistingSessionId() {
        MemorySessionManager manager = new MemorySessionManager();
        manager.createSessionMap("known");

        DefaultHttpSession known = new DefaultHttpSession(manager, "known");
        assertEquals("known", known.sessionId(), "存在的会话 id 被直接采用");

        DefaultHttpSession unknown = new DefaultHttpSession(manager, "missing");
        assertNull(unknown.sessionId(), "不存在的会话 id 不被采用");
        unknown.setAttr("k", "v");
        assertNotNull(unknown.sessionId());
        assertNotEquals("missing", unknown.sessionId(), "懒创建时生成新的 id");
    }

    @Test
    @DisplayName("sessionId 传 null 等同于无参构造，不创建会话")
    void constructorWithNullSessionIdCreatesNothing() {
        DefaultHttpSession session = new DefaultHttpSession(new MemorySessionManager(), null);
        assertNull(session.sessionId());
    }

    @Test
    @DisplayName("路径默认 /，setPath 后 pathIsUpdate 置真（Set-Cookie 重写 Path 的触发条件）")
    void pathDefaultsToRootUntilSet() {
        DefaultHttpSession session = new DefaultHttpSession(new MemorySessionManager());

        assertEquals("/", session.getPath());
        assertFalse(session.pathIsUpdate(), "新会话路径未更新");

        session.setPath("/admin");

        assertEquals("/admin", session.getPath());
        assertTrue(session.pathIsUpdate(), "setPath 后必须置位，否则响应不会重写 Cookie 的 Path");
    }

    @Test
    @DisplayName("createSessionMap 连续 5 次失败后抛 HttpSessionException")
    void retryExhaustionThrowsHttpSessionException() {
        StubSessionManager manager = new StubSessionManager();
        manager.createSessionMapResult = false;   // 每次都失败，逼出重试耗尽分支
        DefaultHttpSession session = new DefaultHttpSession(manager);

        HttpSessionException ex = assertThrows(HttpSessionException.class, () -> session.setAttr("k", "v"));

        assertEquals("sessionId generate error", ex.getMessage());
        assertNull(session.sessionId(), "抛异常后会话仍是未创建状态");
        assertEquals(5, manager.createSessionMapCalls,
                "初始 id 之后最多重试 MAX_RETRY_COUNT(5) 次，共调用 5 次 createSessionMap");
    }

    /**
     * 最小 SessionManager 桩：只记录 DefaultHttpSession 会触发的调用。
     * 其余方法不会被本测试触发，留空实现；不用 Mockito（spec §2.2 未批准新增测试库）。
     */
    private static final class StubSessionManager implements SessionManager {

        boolean createSessionMapResult = true;
        int createSessionMapCalls;
        int expireAtCalls;
        String lastExpireAtId;

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
            createSessionMapCalls++;
            return createSessionMapResult;
        }

        @Override
        public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {
        }

        @Override
        public String sessionManagerName() {
            return "stub session manager";
        }

        @Override
        public void expireAt(String sessionId) {
            expireAtCalls++;
            lastExpireAtId = sessionId;
        }

        @Override
        public <T> T sessionSafeScope(Function<SessionManager, T> action) {
            return action.apply(this);
        }
    }
}
