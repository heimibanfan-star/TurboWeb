package top.turboweb.http.server.config;

import io.netty.handler.codec.http2.Http2Settings;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ServerConfig} 的默认值与建造者校验矩阵。
 * 校验是"链式方法立即抛出"的风格，因此每个非法参数都单独成例。
 */
class ServerConfigTest {

    @Test
    @DisplayName("默认值符合约定")
    void defaults() {
        int cpu = Runtime.getRuntime().availableProcessors();
        ServerConfig config = ServerConfig.builder().build();

        assertEquals(10000, config.getMaxConnect());
        assertEquals(1, config.getIoThreadNum());
        assertEquals(cpu * 2, config.getZeroCopyThreadNum());
        assertEquals(cpu, config.getCpuNum());
        assertNull(config.getSslContext(), "未配置ssl时上下文为null");
        assertEquals(1024 * 1024 * 10, config.getMaxContentLength());
        assertFalse(config.isEnableHttp2());
        Http2Settings defaults = config.getHttp2Settings();
        assertNotNull(defaults);
        // 这批 getter 在 Netty 里混用 Long 与 int 返回；统一 (long) 转换后必然走
        // assertEquals(long, long)，避免 Integer-vs-Long 的装箱比较假红
        assertEquals(256L, (long) defaults.maxConcurrentStreams());
        assertEquals(1048576L, (long) defaults.initialWindowSize());
        assertEquals(16384L, (long) defaults.maxFrameSize());
        assertEquals(8192L, (long) defaults.maxHeaderListSize());
    }

    @Test
    @DisplayName("maxConnect 必须大于0")
    void maxConnectMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().maxConnect(0));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().maxConnect(-1));
    }

    @Test
    @DisplayName("ioThreadNum 必须大于0")
    void ioThreadNumMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().ioThreadNum(0));
    }

    @Test
    @DisplayName("zeroCopyThreadNum 必须大于0")
    void zeroCopyThreadNumMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().zeroCopyThreadNum(0));
    }

    @Test
    @DisplayName("cpuNum 必须大于0")
    void cpuNumMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().cpuNum(0));
    }

    @Test
    @DisplayName("maxContentLength 不得小于1024")
    void maxContentLengthFloor() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().maxContentLength(1023));
        assertDoesNotThrow(() -> ServerConfig.builder().maxContentLength(1024));
    }

    @Test
    @DisplayName("http2Settings 不能为null")
    void http2SettingsNotNull() {
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.builder().http2Settings(null));
    }

    @Test
    @DisplayName("sslContext 被原样承载（引用相同）")
    void sslContextIsCarriedByReference() {
        SslContext fake = new NoTlsSslContext();
        ServerConfig config = ServerConfig.builder().sslContext(fake).build();
        assertSame(fake, config.getSslContext(), "非 null 上下文必须原样带出，不能被 setter 吞掉");
    }

    /**
     * 夹具：不触碰任何 TLS provider、不读信任库、不建 SSLEngine 的 {@link SslContext} 占位实现。
     * 仅用于把非 null 引用喂给 {@code ServerConfig.Builder.sslContext}，以满足 spec §2.2
     * "不引入 TLS 夹具"——它不建立任何真实 SSL 上下文，所有抽象方法返回 null / 空。
     */
    @SuppressWarnings("deprecation") // Netty 4.2 的 ApplicationProtocolNegotiator 已过时，但仍是抽象方法
    private static final class NoTlsSslContext extends SslContext {

        @Override
        public boolean isClient() {
            return true;
        }

        @Override
        public List<String> cipherSuites() {
            return List.of();
        }

        @Override
        public ApplicationProtocolNegotiator applicationProtocolNegotiator() {
            return null;
        }

        @Override
        public SSLEngine newEngine(ByteBufAllocator alloc) {
            return null;
        }

        @Override
        public SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
            return null;
        }

        @Override
        public SSLSessionContext sessionContext() {
            return null;
        }
    }

    @Test
    @DisplayName("合法参数被原样承载")
    void validValuesAreCarried() {
        Http2Settings settings = new Http2Settings().maxConcurrentStreams(8);
        ServerConfig config = ServerConfig.builder()
                .maxConnect(10)
                .ioThreadNum(2)
                .zeroCopyThreadNum(3)
                .cpuNum(4)
                .maxContentLength(2048)
                .enableHttp2(true)
                .http2Settings(settings)
                .build();

        assertEquals(10, config.getMaxConnect());
        assertEquals(2, config.getIoThreadNum());
        assertEquals(3, config.getZeroCopyThreadNum());
        assertEquals(4, config.getCpuNum());
        assertEquals(2048, config.getMaxContentLength());
        assertTrue(config.isEnableHttp2());
        assertSame(settings, config.getHttp2Settings());
    }
}
