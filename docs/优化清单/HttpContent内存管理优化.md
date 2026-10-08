# HttpContent 内存管理优化清单

**登记日期**：2026-10-08
**来源**：排查台账 HS-5（`HttpContent.release()` 解析过表单后不幂等）时暴露的问题面——release/销毁链路只是表象，根子上是**请求体相关内存（decoder、缓冲、解析缓存）的生命周期没有系统管理**。
**定位**：后续"内存管理专项"的候选优化项预立项记录。**2026-10-08 更新：HS-5 已裁决 ☑（后续优化）（测试报告 §10.19），本清单即该裁决的承接载体**，P0 是其修复落点；清单其余内容不构成裁决（修/不修仍走台账 §5 流程）。

---

## 一、优化状态

### 待处理

| 编号 | 项 | 优先级 | 立项日期 |
| --- | --- | --- | --- |
| P0 | release 幂等化 + 释放即清空 | 高 | 2026-10-08 |
| P1 | release 契约收敛 | 高 | 2026-10-08 |
| P2 | 数据工厂内存上限治理 | 中 | 2026-10-08 |
| P3 | 缓冲归属与生命周期图 | 中 | 2026-10-08 |
| P4 | 泄漏防护常态化【待核实】 | 低 | 2026-10-08 |

### 已处理

（暂无——条目完成后从"待处理"整条移入，注明处理提交号与日期）

---

## 二、现状事实（代码锚点，2026-10-08 核对）

| # | 事实 | 位置 |
| --- | --- | --- |
| 1 | `release()` 只 `decoder.destroy()`，不置 null、无 released 标志位 → 二次调用命中 Netty `checkDestroyed()` 抛 `IllegalStateException`（即 HS-5） | `HttpContent.java:214-218` |
| 2 | destroy 后 `formParams`/`formFiles`/`jsonContent`/`formIsParsed` 字段原样留存——decoder 已销毁而缓存视图仍在，对象若被上下文挂载带走，引用悬空（数据侧已释放） | `HttpContent.java:193-195`、字段区 :33-55 |
| 3 | 表单数据工厂阈值 `DefaultHttpDataFactory(Integer.MAX_VALUE)`：内存优先、~2GB 才转磁盘，实践上 multipart 全部驻留堆内，大文件上传无背压 | `HttpContent.java:158-162` |
| 4 | 框架侧唯一生产 release 点在 `MiddlewareInvokeProcessor` 的 `finally`（每请求恰一次）；业务再调 `ctx.release()` 即构成双 release | `MiddlewareInvokeProcessor.java:144-149` |
| 5 | release 契约口径矛盾：`HttpContext` 接口 Javadoc"典型用法"教业务在 handler 里 `ctx.release()`（`HttpContext.java:38-39`），`FullHttpContext.release()` Javadoc 又说"通常在请求结束后由框架自动调用"（`FullHttpContext.java:427-428`） | 两处 javadoc |
| 6 | Netty 引用计数 release（`httpRequest.release()`/`fullHttpResponse.release()`，自带 refCnt 保护、抛 `IllegalReferenceCountException`）与 turboweb `HttpContent.release()` 撞名并存，两套机制无统一说明，排查影响面时易混（CodeGraph 已实测混入） | `RequestSerializerHandler.java:76-84`、`ClientResult.java:120-124` |
| 7 | SSE/异步路径：中间件链 `finally` release 时流可能仍在推（`ConnectSession` 生命周期与请求上下文生命周期不同步），缓冲归属未梳理【待核实具体面】 | `InternalSseEmitter` 链路 |

---

## 三、处理方案

### P0 · release 幂等化 + 释放即清空（HS-5 的直接修正与其完整口径）

- `decoder.destroy()` 后置 `decoder = null`（或加 `released` 标志位），二次 release 变 no-op；
- 同步清空 `formParams`、`formFiles`、`jsonContent` 缓存（`formIsParsed` 语义一并梳理：清空后再取表单应重新 parse 还是抛"已释放"，需定契约）；
- 钉住用例 `releaseAfterFormParsingIsNotRepeatable` 翻转为修复期望。

### P1 · release 契约收敛

- 三选一钉死：a) 业务侧禁止调用，`HttpContext` javadoc 示例删去 `ctx.release()`；b) 幂等化后允许重复调用（依赖 P0）；c) 上下文自持 released 标志，二次业务调用静默吞并 warn。
- 无论选哪种，`FullHttpContext`/`HttpContent`/`HttpContext` 三处 javadoc 与实现同口径（消解事实 5）。

### P2 · 数据工厂内存上限治理

- `DefaultHttpDataFactory` 阈值从写死 `Integer.MAX_VALUE` 改为可配置（`HttpConfig` 增设字段），或换混合工厂按大小自动转磁盘；
- 超大 multipart 上传的拒绝/限幅策略（与 HS-3/HS-4 所在限流面是否复用待定）。

### P3 · 缓冲归属与生命周期图

- 梳理"谁创建、谁持有、谁释放"三张表：`request.content()`（Netty refCnt）、decoder 派生的 `Attribute`/`FileUpload`、`HttpContent` 自身缓存；
- SSE/异步场景单独核对事实 7；
- 两套"release"命名区分建议在 javadoc 明确（Netty 引用计数 vs 框架 decoder 销毁），或给框架侧考虑改名（涉及公共 API，慎重）。

### P4 · 泄漏防护常态化【待核实】

- 确认装配处是否设置 `io.netty.leakDetection.level`（默认 SIMPLE）；测试程序是否加 `ResourceLeakDetector` 探针跑一轮请求体路径。

### 落地节奏

1. HS-5 台账已关账（☑ 后续优化，§10.19），P0 并入本专项推进；P0 落地时走修复流程（RED 翻转 → 最小实现 → 全量回归 → 修复+回写两笔），台账侧仅对 HS-5 处置列补记修复溯源。
2. P0+P1 同属 release 语义，宜一个专项提交带回归；P2 独立（改配置面，动 `HttpConfig`）；P3/P4 是排查性任务，先出结论再立项改动。
3. 若专项排查中撞出新缺陷，HS 编号从 **HS-26** 起顺延（只增不迁）。
4. 每步改动照例：RED 先行（钉住用例翻转/新增）→ 最小实现 → 全量回归（当前基线 703）→ 修复与回写两笔提交。
5. 条目完成后：从 §一"待处理"整条移入"已处理"，注明提交号与日期；P0–P4 全部了结后本文件移入 `docs/优化清单/归档/2026-XX/`（规则见 `.claude/rules/优化清单.md`）。
