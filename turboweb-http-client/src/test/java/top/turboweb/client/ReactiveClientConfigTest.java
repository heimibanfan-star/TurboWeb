package top.turboweb.client;

import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 响应式客户端配置的边界与语义：默认值、全量自定义、透传口同一性、
 * 数值与时长校验、缺省排队数量口径与建造者的可重复产出。
 */
class ReactiveClientConfigTest {

    @Nested
    @DisplayName("默认值")
    class Defaults {

        @Test
        @DisplayName("缺省装配取各默认数值与关闭态开关")
        void defaultValues() {
            ReactiveClientConfig config = ReactiveClientConfig.builder().build();

            assertEquals(500, config.maxConnections());
            assertEquals(1000, config.pendingAcquireMaxCount());
            assertEquals(Duration.ofSeconds(45), config.pendingAcquireTimeout());
            assertNull(config.maxIdleTime());
            assertNull(config.maxLifeTime());
            assertEquals(Duration.ofSeconds(10), config.connectTimeout());
            assertEquals(Duration.ofSeconds(30), config.responseTimeout());
            assertEquals(1, config.eventLoopCount());
            assertEquals(false, config.compression());
            assertEquals(false, config.followRedirect());
            assertEquals(false, config.wiretap());
            assertNull(config.connectionProvider());
            assertNull(config.sslProvider());
            assertNull(config.proxyOptions());
        }
    }

    @Nested
    @DisplayName("自定义与透传")
    class CustomAndPassthrough {

        @Test
        @DisplayName("全量自定义各项取值生效")
        void customValues() throws Exception {
            ConnectionProvider pool = ConnectionProvider.builder("cfg-pool").build();
            SslProvider ssl = SslProvider.builder()
                    .sslContext(SslContextBuilder.forClient().build())
                    .build();
            Consumer<ProxyProvider.TypeSpec> proxy =
                    spec -> spec.type(ProxyProvider.Proxy.HTTP).host("127.0.0.1").port(1);

            ReactiveClientConfig config = ReactiveClientConfig.builder()
                    .connectionProvider(pool)
                    .sslProvider(ssl)
                    .proxyOptions(proxy)
                    .maxConnections(64)
                    .pendingAcquireMaxCount(128)
                    .pendingAcquireTimeout(Duration.ofSeconds(5))
                    .maxIdleTime(Duration.ofSeconds(15))
                    .maxLifeTime(Duration.ofMinutes(2))
                    .connectTimeout(Duration.ofSeconds(3))
                    .responseTimeout(Duration.ofSeconds(8))
                    .eventLoopCount(4)
                    .compression(true)
                    .followRedirect(true)
                    .wiretap(true)
                    .build();

            assertSame(pool, config.connectionProvider());
            assertSame(ssl, config.sslProvider());
            assertSame(proxy, config.proxyOptions());
            assertEquals(64, config.maxConnections());
            assertEquals(128, config.pendingAcquireMaxCount());
            assertEquals(Duration.ofSeconds(5), config.pendingAcquireTimeout());
            assertEquals(Duration.ofSeconds(15), config.maxIdleTime());
            assertEquals(Duration.ofMinutes(2), config.maxLifeTime());
            assertEquals(Duration.ofSeconds(3), config.connectTimeout());
            assertEquals(Duration.ofSeconds(8), config.responseTimeout());
            assertEquals(4, config.eventLoopCount());
            assertEquals(true, config.compression());
            assertEquals(true, config.followRedirect());
            assertEquals(true, config.wiretap());
        }

        @Test
        @DisplayName("排队数量缺省按最大连接数两倍，显式配置以显式为准")
        void pendingAcquireMaxCountResolution() {
            assertEquals(14, ReactiveClientConfig.builder().maxConnections(7).build().pendingAcquireMaxCount());
            assertEquals(11, ReactiveClientConfig.builder()
                    .maxConnections(7).pendingAcquireMaxCount(11).build().pendingAcquireMaxCount());
        }

        @Test
        @DisplayName("可选时长显式置空表示不启用对应限制")
        void optionalDurationsAcceptNull() {
            ReactiveClientConfig config = ReactiveClientConfig.builder()
                    .responseTimeout(null)
                    .maxIdleTime(null)
                    .maxLifeTime(null)
                    .build();

            assertNull(config.responseTimeout());
            assertNull(config.maxIdleTime());
            assertNull(config.maxLifeTime());
        }

        @Test
        @DisplayName("建造者可重复产出，先产出的配置不受后续改动影响")
        void buildIsRepeatableAndIndependent() {
            ReactiveClientConfig.Builder builder = ReactiveClientConfig.builder().maxConnections(5);
            ReactiveClientConfig first = builder.build();

            builder.maxConnections(9).eventLoopCount(3);
            ReactiveClientConfig second = builder.build();

            assertEquals(5, first.maxConnections());
            assertEquals(1, first.eventLoopCount());
            assertEquals(9, second.maxConnections());
            assertEquals(3, second.eventLoopCount());
            assertNotEquals(first, second);
        }
    }

    @Nested
    @DisplayName("校验")
    class Validation {

        @Test
        @DisplayName("最大连接数小于一拒绝")
        void maxConnectionsMustBePositive() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().maxConnections(0).build());
            assertEquals("maxConnections must be positive", e.getMessage());
        }

        @Test
        @DisplayName("数值一为合法边界")
        void valueOneIsLegalBoundary() {
            assertEquals(1, ReactiveClientConfig.builder().maxConnections(1).build().maxConnections());
            assertEquals(1, ReactiveClientConfig.builder().eventLoopCount(1).build().eventLoopCount());
        }

        @Test
        @DisplayName("事件循环数量小于一拒绝")
        void eventLoopCountMustBePositive() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().eventLoopCount(0).build());
            assertEquals("eventLoopCount must be positive", e.getMessage());
        }

        @Test
        @DisplayName("必填时长为空拒绝")
        void requiredDurationsMustNotBeNull() {
            assertThrows(NullPointerException.class,
                    () -> ReactiveClientConfig.builder().pendingAcquireTimeout(null).build());
            assertThrows(NullPointerException.class,
                    () -> ReactiveClientConfig.builder().connectTimeout(null).build());
        }

        @Test
        @DisplayName("必填时长零值与负值拒绝")
        void requiredDurationsMustBePositive() {
            assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().pendingAcquireTimeout(Duration.ZERO).build());
            assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().connectTimeout(Duration.ofSeconds(-1)).build());
        }

        @Test
        @DisplayName("可选时长非空时零值与负值拒绝")
        void optionalDurationsMustBePositiveWhenPresent() {
            assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().maxIdleTime(Duration.ZERO).build());
            assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().maxLifeTime(Duration.ofSeconds(-2)).build());
            assertThrows(IllegalArgumentException.class,
                    () -> ReactiveClientConfig.builder().responseTimeout(Duration.ZERO).build());
        }
    }
}
