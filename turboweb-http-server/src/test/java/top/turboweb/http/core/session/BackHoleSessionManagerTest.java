package top.turboweb.http.core.session;
import top.turboweb.http.session.SessionManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BackHoleSessionManager} 的空实现契约：写操作无副作用、查询返回固定值、
 * 会话永远“创建成功但不存在”。spec §5 的“跳过读写锁”在当前源码里就是这个空壳，
 * 其安全作用域直接透传回调。
 */
class BackHoleSessionManagerTest {

    private final BackHoleSessionManager manager = new BackHoleSessionManager();

    @Test
    @DisplayName("写入、删除、续时都是无副作用空操作，读取恒为 null")
    void writesAreNoOpsAndReadsAreNull() {
        assertDoesNotThrow(() -> manager.setAttr("s", "k", "v"));
        assertDoesNotThrow(() -> manager.setAttr("s", "k", "v", 1000L));
        assertDoesNotThrow(() -> manager.remAttr("s", "k"));
        assertDoesNotThrow(() -> manager.expireAt("s"));

        assertNull(manager.getAttr("s", "k"), "写过的属性也读不回来");
        assertNull(manager.getAttr("s", "k", String.class));
    }

    @Test
    @DisplayName("存在性恒 false、创建恒 true，且创建不产生任何副作用")
    void existenceAndCreationAreFixed() {
        assertFalse(manager.exist("s"));
        assertTrue(manager.createSessionMap("s"), "黑洞管理器不真正建会话，创建恒报成功");
        assertFalse(manager.exist("s"), "创建返回 true 之后仍然不存在，证明没有落库副作用");
        assertDoesNotThrow(() -> manager.sessionGC(1L, 1L, 1L), "GC 是空实现，不启线程也不抛");
    }

    @Test
    @DisplayName("名称标识固定为 backHole session manager")
    void nameIsFixed() {
        assertEquals("backHole session manager", manager.sessionManagerName());
    }

    @Test
    @DisplayName("安全作用域透传回调：不施加互斥，返回值原样传出")
    void safeScopeRunsActionDirectly() {
        assertEquals("backHole session manager", manager.sessionSafeScope(SessionManager::sessionManagerName),
                "回调收到本管理器，返回值原样透传");
    }
}
