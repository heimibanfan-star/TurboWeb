# HTTPS

由于 HTTP 协议以明文方式传输数据，容易遭受窃听、篡改等安全威胁，因此 **对外暴露的服务强烈推荐使用 HTTPS**。

## 快速启用 HTTPS

在 **TurboWeb** 中启用 HTTPS 十分简单，仅需一行代码即可完成配置。
下面以 **自签名证书** 为示例：

```java
public class HttpsApplication {
    public static void main(String[] args) throws CertificateException, SSLException {
        // 创建自签名证书（仅用于示例）
        SelfSignedCertificate certificate = new SelfSignedCertificate();

        // 构建 SSL 上下文
        SslContext sslContext = SslContextBuilder.forServer(
                certificate.certificate(),   // 证书
                certificate.privateKey()     // 私钥
        ).build();

        // 启动支持 HTTPS 的 TurboWeb 服务器
        BootStrapTurboWebServer.create()
                .http()
                .middleware(new Middleware() {
                    @Override
                    public Object invoke(HttpContext ctx) {
                        return "hello world";
                    }
                })
                .and()
                // 配置 SSL，上下文设置完成后即可启用 HTTPS
                .ssl(sslContext)
                .start();
    }
}
```

启动后，该服务将仅支持通过 **HTTPS** 协议访问，**HTTP 将自动失效**。

> ⚠️ **注意**
>  上述示例中使用的自签名证书仅用于本地开发和测试。
>  在生产环境中，请务必使用由受信任的 CA（证书颁发机构）签发的正式证书。

## 性能与兼容性说明

当 TurboWeb 启用 HTTPS 后：

- **零拷贝文件传输** 功能将自动失效（由于加密需要在用户空间完成）；
- 框架会自动降级为 **原生 `NioSocketChannel` 模式**；
- 不再创建零拷贝专用线程池。

此设计确保在启用加密的前提下，系统的稳定性与兼容性保持最佳。



[首页](../README.md) | [三级限流保护体系](./limiter.md) | [走向HTTP2.0](http2.md)