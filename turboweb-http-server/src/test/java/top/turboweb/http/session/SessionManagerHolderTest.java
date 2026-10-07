package top.turboweb.http.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link SessionManagerHolder} 契约经其唯一实现 {@link DefaultSessionManagerHolder} 验证。
 *
 * <p>接口本身没有可执行行（JaCoCo 不计抽象方法），所以断言落在实现的构造注入与 GC 启动参数上：
 * 构造时必须把 check / maxNotUse / threshold 原样透传给 {@link SessionManager#sessionGC}。
 */
class SessionManagerHolderTest {

    @Test
    @DisplayName("构造时按给定 check/maxNotUse/threshold 原样启动 GC，getSessionManager 返回注入实例")
    void constructorStartsGcWithGivenArguments() {
        RecordingSessionManager manager = new RecordingSessionManager();

        DefaultSessionManagerHolder holder = new DefaultSessionManagerHolder(manager, 111L, 222L, 333L);

        assertEquals(1, manager.gcCalls, "构造器应恰好调用一次 sessionGC");
        // 三个参数取互不相同的值，任何串位都会让对应断言红
        assertEquals(111L, manager.checkTime, "checkTime 原样透传");
        assertEquals(222L, manager.maxNotUseTime, "maxNotUseTime 原样透传");
        assertEquals(333L, manager.threshold, "会话数阈值原样透传");
        assertSame(manager, holder.getSessionManager(), "getSessionManager 返回注入的同一实例");
    }

    /** 记录 sessionGC 调用与三个入参的桩；其余方法不会被触发。 */
    private static final class RecordingSessionManager implements SessionManager {

        int gcCalls;
        long checkTime;
        long maxNotUseTime;
        long threshold;

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
            gcCalls++;
            this.checkTime = checkTime;
            this.maxNotUseTime = maxNotUseTime;
            this.threshold = sessionNumThreshold;
        }

        @Override
        public String sessionManagerName() {
            return "recording session manager";
        }

        @Override
        public void expireAt(String sessionId) {
        }
    }
}
