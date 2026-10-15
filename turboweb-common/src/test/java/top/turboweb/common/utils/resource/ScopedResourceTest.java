package top.turboweb.common.utils.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScopedResource} 的生命周期契约。
 * 这是资源作用域的基准用例：http/websocket 应用的关闭行为都建立在它之上。
 */
class ScopedResourceTest {

    /** 记录关闭事件的测试资源：sink 的顺序即释放顺序 */
    private static final class Recorded implements AutoCloseable {
        private final String name;
        private final List<String> sink;
        private final boolean boom;

        Recorded(String name, List<String> sink, boolean boom) {
            this.name = name;
            this.sink = sink;
            this.boom = boom;
        }

        @Override
        public void close() {
            if (boom) {
                throw new IllegalStateException("故意失败:" + name);
            }
            sink.add(name);
        }
    }

    @Test
    @DisplayName("释放顺序与登记顺序一致")
    void releaseFollowsRegistrationOrder() {
        List<String> sink = new ArrayList<>();
        ScopedResource scoped = new ScopedResource();
        scoped.register(new Recorded("first", sink, false));
        scoped.register(new Recorded("second", sink, false));
        scoped.register(new Recorded("third", sink, false));

        scoped.close();

        assertEquals(List.of("first", "second", "third"), sink);
    }

    @Test
    @DisplayName("close 幂等：重复调用不二次释放")
    void closeIsIdempotent() {
        List<String> sink = new ArrayList<>();
        ScopedResource scoped = new ScopedResource();
        scoped.register(new Recorded("only", sink, false));

        scoped.close();
        scoped.close();

        assertEquals(List.of("only"), sink);
    }

    @Test
    @DisplayName("释放后拒绝登记")
    void registerAfterCloseThrows() {
        ScopedResource scoped = new ScopedResource();
        scoped.register(new Recorded("a", new ArrayList<>(), false));
        scoped.close();

        assertThrows(IllegalStateException.class,
                () -> scoped.register(new Recorded("b", new ArrayList<>(), false)));
    }

    @Test
    @DisplayName("同一资源实例重复登记被拒绝")
    void duplicateRegistrationThrows() {
        List<String> sink = new ArrayList<>();
        Recorded resource = new Recorded("dup", sink, false);
        ScopedResource scoped = new ScopedResource();
        scoped.register(resource);

        assertThrows(IllegalStateException.class, () -> scoped.register(resource));
    }

    @Test
    @DisplayName("单个资源释放失败不阻断其余释放")
    void failingResourceDoesNotBlockOthers() {
        List<String> sink = new ArrayList<>();
        ScopedResource scoped = new ScopedResource();
        scoped.register(new Recorded("boom", sink, true));
        scoped.register(new Recorded("after", sink, false));

        scoped.close();

        // boom 抛出前未写入 sink，after 必须仍被释放
        assertEquals(List.of("after"), sink);
    }

    @Test
    @DisplayName("登记 null 立即抛 NPE")
    void registerNullThrows() {
        ScopedResource scoped = new ScopedResource();

        assertThrows(NullPointerException.class, () -> scoped.register(null));
    }
}
