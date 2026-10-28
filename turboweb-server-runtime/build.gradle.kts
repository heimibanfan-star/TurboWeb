/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予；
 * 本模块为契约包：ServerRuntime 的装配签名只暴露 netty 的 ChannelPipeline，不背 codec-http 编解码整包。
 */
plugins {
    // 版本由根构建脚本的 plugins 块统一声明，此处按坐标启用
    id("info.solidsoft.pitest")
}

dependencies {
    // ChannelPipeline 出现在接口签名上，以 api 对外暴露：契约消费方经传递依赖拿到该类型
    api(libs.netty.transport)

    // Gradle 9 不再自带 platform launcher；BOM 复用既有的 junit-jupiter 版本，不引入第二个版本号
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

description = "turboweb-server-runtime"

// 覆盖率闸：分母即模块全部主码类，线覆盖钉 0.90
tasks.named<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// 变异作用域为模块全部主码类；变异评分钉 95，pitest 任务独立红绿不接 check（与全仓口径一致）
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")
    // 本仓存在顶满 30s 的定时敏感用例，默认 1.2 倍超时窗会把慢用例误判为超时，放宽
    timeoutFactor.set(4.toBigDecimal())
    targetClasses.set(listOf("top.turboweb.server.runtime.*"))
    // netty InternalLogger 调用点不参与变异：只差日志文案的分支是等价变异体，靠日志凑分无意义
    avoidCallsTo.set(setOf("io.netty.util.internal.logging"))
    mutationThreshold.set(95)
}
