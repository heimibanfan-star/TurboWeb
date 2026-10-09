# httpcore5 依赖移除优化

- **来源 / 定位**：由用户于 2026-10-09 在 HS-21 处置批次中明确立项，主题为**后续项目移除对 `org.apache.httpcomponents.core5:httpcore5` 的依赖**。本清单是结构性优化承接，**不构成对任何缺陷台账条目的裁决**（缺陷裁决仍走 `docs/测试报告/` §5 流程）。
- **核对日期**：2026-10-09（以下行号均按当日磁盘源码核对）。

## 优化状态

### 待处理

| 编号 | 项 | 优先级 | 立项日期 |
| --- | --- | --- | --- |
| 1 | 全仓移除 httpcore5 依赖：以仓内自有值对象与 JDK 能力替换 ContentType / NameValuePair / URIBuilder 三类消费点，收口公开 API 第三方类型泄漏 | P2 | 2026-10-09 |

### 已处理

（暂无）

## 现状与问题

### 依赖声明与传播面

- 唯一声明点在 build-logic 约定插件 `build-logic/src/main/kotlin/buildlogic.java-conventions.gradle.kts:22`：`api("org.apache.httpcomponents.core5:httpcore5:5.3.1")`。以 **api** 方式随约定授予全部子模块，并**传递进下游使用者的编译/运行 classpath**。

### 主代码消费面（9 文件，符号仅三种）

| 模块 | 文件 | 所用 httpcore5 符号 |
| --- | --- | --- |
| http-server | `top/turboweb/http/processor/MiddlewareInvokeProcessor.java:7` | ContentType |
| http-server | `top/turboweb/http/response/AbstractFileResponse.java:4` | ContentType |
| http-server | `top/turboweb/http/response/ReactorResponse.java:8` | ContentType |
| http-server | `top/turboweb/http/context/respmeta/ResponseMeta.java:4` | ContentType |
| http-server | `top/turboweb/http/context/respmeta/ResponseMetaGetter.java:4` | ContentType |
| http-server | `top/turboweb/http/context/respmeta/DefaultResponseMeta.java:4` | ContentType |
| http-server | `top/turboweb/http/context/FullHttpContext.java:6-7` | NameValuePair、URIBuilder |
| http-client | `top/turboweb/client/DefaultTurboHttpClient.java:6-7` | ContentType、URIBuilder |
| http-client | `top/turboweb/client/converter/JsonConverter.java:8` | ContentType |
| gateway | `top/turboweb/gateway/filter/ResponseHelper.java:6` | ContentType |
| gateway | `top/turboweb/gateway/filter/DefaultResponseHelper.java:8` | ContentType |

测试面另有 4 文件 import ContentType（`ReactorResponseTest`、`AbstractFileResponseTest`、`ResponseMetaTest`、`MiddlewareInvokeProcessorTest`）。

实际用法收敛为三类：**ContentType 值对象**（创建、mimeType 与 charset 取值，占绝大多数）、**URIBuilder**（查询参数解析 / URI 构造两处）、**NameValuePair**（随 URIBuilder 返回值迭代）。

### 存在的问题

1. **依赖分量失配**：整包 httpcore5 仅承担 content-type 值对象与 URI 查询解析的轻职责，wire 层编解码本来就全部由 Netty 承担，该依赖属旁挂的第三方小工具。
2. **公开 API 泄漏第三方类型**：`ReactorResponse` 构造器与 `setContentType`、`AbstractFileResponse.setContentType`、`ResponseMeta` 一族接口签名直接以 `org.apache.hc.core5.http.ContentType` 作参数 / 返回类型——httpcore5 后续版本（5.x 线）升级或本框架换依赖，都会**直接破坏下游编译**。
3. **api 传递扩大影响半径**：下游只要引入本框架任意模块即被动携带 httpcore5，无法按需剔除。
4. **解码语义替换风险**：`FullHttpContext` 的 URIBuilder 解码通道是 HS-15 已裁决的既定参数解码语义的落点（form 编码口径，与查询侧同族），替换实现若改变解码行为即构成行为回归，须以既有钉住 / 行为锚用例为准绳等语义复刻。

## 处理方案

1. **自建 ContentType 值对象**：在 commons（或 http-server 内）落一个轻量值类型——mime 子类型 + charset + 参数表，parse / format 语义对齐现消费点用到的 `create` / `getMimeType` / `getCharset` / 参数取值；单测先行覆盖解析边界（无 charset、多参数、大小写、空白）。
2. **URIBuilder / NameValuePair 替换**：查询解析与 URI 构造改用 JDK `URI` 或仓内既有工具等语义复刻；**解码行为必须与 HS-15 既定 form 编码口径逐字符一致**，以 ResponseMeta / 上下文既有用例回归把关。
3. **消费面分模块替换**：http-server（7 文件）→ http-client（2 文件）→ gateway（2 文件），每模块替换后跑该模块 check；测试面 4 文件同步换 import。
4. **公开签名收口（破坏性）**：ContentType 入参 / 出参的公共 API 换为仓内值对象；迁移方式二选一，届时由用户裁决——(a) 大版本一次性换；(b) 过渡期保留旧签名重载并标废弃。
5. **依赖摘除与审计**：删除 `buildlogic.java-conventions.gradle.kts:22` 的 api 行，`gradlew dependencies` 全仓审计 `org.apache.hc` 零残留；冷跑全链 `check --rerun-tasks`，两闸按当批账面回写。
6. **落地节奏**：独立专项推进，不夹带于缺陷修复批次；条目完成后整条移入"已处理"并记处理提交号与日期，本篇"待处理"清空后整文件移入 `docs/优化清单/归档/yyyy-MM/`。
