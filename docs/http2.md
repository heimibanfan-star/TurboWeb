# HTTP2.0

**HTTP/2.0** 是对传统 **HTTP/1.1** 协议的一次重大升级，旨在解决旧协议在现代网络环境下的性能瓶颈。
它仍然基于 **TCP** 协议，但在传输层面进行了系统性优化：采用 **二进制分帧机制** 将请求与响应拆分为更细粒度的帧（Frame）进行传输，显著提升通信效率；支持 **多路复用（Multiplexing）**，在同一连接中可同时并行多个请求与响应，彻底消除了 HTTP/1.1 的“队头阻塞”问题；并引入 **头部压缩（HPACK）**，减少重复请求头带来的带宽浪费。

相较于 HTTP/1.1，HTTP/2.0 提供了更优的传输性能与更流畅的用户体验。
它减少了连接建立的次数，提高了数据传输效率，显著降低延迟；同时支持 **服务器推送（Server Push）** 机制，能够在客户端发起请求前主动推送关键资源，加快页面加载速度。总体而言，HTTP/2.0 使 Web 应用在高并发、复杂页面及移动网络场景下的性能表现更加出色，已成为现代 Web 通信的主流标准。

## 快速启用HTTP2.0

在 **TurboWeb** 中，HTTP/2.0 的启用被高度封装，只需在启用 HTTPS 的基础上调用 `.enableHttp2()` 即可。
由于浏览器普遍要求 HTTP/2 使用 TLS（即 **HTTPS**），因此必须先配置 **SSL 证书（`SslContext`）**，框架才能通过 ALPN 协议协商启用 HTTP/2。

```java
public class Http2Application {
    public static void main(String[] args) throws CertificateException, SSLException {
        SelfSignedCertificate certificate = new SelfSignedCertificate();
        SslContext sslContext = SslContextBuilder.forServer(
                        // 配置证书
                        certificate.certificate(),
                        // 配置私钥
                        certificate.privateKey()
                )
                .protocols("TLSv1.3", "TLSv1.2")
            	// 构建 SSL 上下文，开启 ALPN 协议协商
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        BootStrapTurboWebServer.create()
                .http()
                .middleware(new Middleware() {
                    @Override
                    public Object invoke(HttpContext ctx) {
                        return "hello world";
                    }
                })
                .and()
                // 在此配置sslContext即可完成https的配置
                .ssl(sslContext)
                // 启用http2
                .enableHttp2()
                .start();
    }
}
```

 **注意：**

- 必须配置 `SslContext`（即 HTTPS）才能启用 HTTP/2。
- 框架会自动通过 **ALPN（Application-Layer Protocol Negotiation）** 与客户端协商使用 HTTP/2 或 HTTP/1.1。
- 不支持 HTTPS 的客户端将自动回退至 HTTP/1.1。

## TurboWeb HTTP2.0的特点

启用 HTTP/2.0 后，TurboWeb 会自动注册 **协议协商器（ApplicationProtocolNegotiationHandler）**，在握手阶段根据客户端能力动态选择通信协议。
对于支持 HTTP/2.0 的客户端，框架会自动建立二进制帧通信通道；而对于仅支持 HTTP/1.1 的客户端，则自动回退到传统协议，确保兼容性。

在实现层面，TurboWeb 内部通过 `Http2FrameAdaptorHandler` 自动完成 **HTTP/1.1 与 HTTP/2.0 请求对象的双向转换**。
框架底层基于 Netty 的 `Http2FrameCodec` 与 `Http2MultiplexHandler`，完全自动化地实现了以下特性：

- ✅ **请求与响应分帧传输**（Frame-based Transmission）
- ✅ **多路复用处理**（Concurrent Stream Multiplexing）
- ✅ **头部压缩**（HPACK Header Compression）
- ✅ **透明协议切换**（Automatic Protocol Negotiation）

对开发者而言，HTTP/2.0 的使用体验与 HTTP/1.1 完全一致，无需关心底层协议差异。
TurboWeb 会在后台自动处理帧压缩、多流调度及连接复用逻辑，让应用以最简洁的方式获得 HTTP/2.0 带来的性能优势。



[首页](../README.md) | [HTTPS](./https.md) | [HTTP客户端](./httpclient.md)

