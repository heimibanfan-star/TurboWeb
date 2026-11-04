package top.turboweb.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpAddress;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.HttpSchemes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoteHttpPortFactoryTest {

    /**
     * 最小引擎桩：仅满足接口契约，不承载传输行为。
     */
    private static HttpClientEngine stubEngine() {
        return new HttpClientEngine() {
            @Override
            public ByteResponseMessage request(HttpRequestMessage<byte[]> request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StreamHandle stream(HttpRequestMessage<byte[]> request, StreamHandler handler) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
            }
        };
    }

    /**
     * 按主机名构造 http 地址，供各用例拼装命名空间。
     */
    private static HttpAddress addr(String host) {
        return HttpAddress.of(HttpSchemes.Http, host);
    }

    @Test
    @DisplayName("建造后两属性可按建造值读出")
    void buildExposesConfiguredProperties() {
        HttpClientEngine engine = stubEngine();
        HttpAddress x = addr("x");
        HttpAddress y = addr("y");
        HttpAddress z = addr("z");

        RemoteHttpPortFactory factory = RemoteHttpPortFactory.builder()
                .engine(engine)
                .namespace("a", x, y)
                .namespace("b", z)
                .build();

        assertSame(engine, factory.engine());
        assertArrayEquals(new HttpAddress[]{x, y}, factory.namespaces().get("a"));
        assertArrayEquals(new HttpAddress[]{z}, factory.namespaces().get("b"));
        assertEquals(2, factory.namespaces().size());
    }

    @Test
    @DisplayName("引擎缺位时建造拒绝")
    void buildRejectsMissingEngine() {
        assertThrows(NullPointerException.class, () -> RemoteHttpPortFactory.builder()
                .engine(null)
                .build());
    }

    @Test
    @DisplayName("命名空间映射不可再修改")
    void namespacesMapIsUnmodifiable() {
        RemoteHttpPortFactory factory = RemoteHttpPortFactory.builder()
                .engine(stubEngine())
                .namespace("a", addr("x"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> factory.namespaces().put("c", new HttpAddress[]{addr("v")}));
    }

    @Test
    @DisplayName("建造后修改外部入参不影响工厂持有的映射")
    void externalMutationDoesNotAffectFactory() {
        HttpAddress x = addr("x");
        Map<String, HttpAddress[]> external = new HashMap<>();
        external.put("a", new HttpAddress[]{x});

        RemoteHttpPortFactory factory = RemoteHttpPortFactory.builder()
                .engine(stubEngine())
                .namespaces(external)
                .build();

        external.put("b", new HttpAddress[]{addr("y")});
        external.get("a")[0] = addr("mutated");

        assertEquals(1, factory.namespaces().size());
        assertSame(x, factory.namespaces().get("a")[0]);
    }

    @Test
    @DisplayName("读出的命名空间与内部持有隔离")
    void returnedNamespacesAreIsolated() {
        HttpAddress x = addr("x");
        RemoteHttpPortFactory factory = RemoteHttpPortFactory.builder()
                .engine(stubEngine())
                .namespace("a", x)
                .build();

        HttpAddress[] first = factory.namespaces().get("a");
        first[0] = addr("mutated");

        assertNotSame(first, factory.namespaces().get("a"));
        assertSame(x, factory.namespaces().get("a")[0]);
    }
}
