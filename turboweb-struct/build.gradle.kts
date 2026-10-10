/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予；
 * 基数树为本地实现，无第三方运行依赖。
 */
plugins {
    // 版本由根构建脚本的 plugins 块统一声明，此处按坐标启用
    id("info.solidsoft.pitest")
}

dependencies {
    // Gradle 9 不再自带 platform launcher；BOM 复用既有的 junit-jupiter 版本，不引入第二个版本号
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

description = "turboweb-struct"

// 覆盖率闸：分母即模块全部主码类（接口无可执行行，不计入），线覆盖钉 1.00
tasks.named<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// 变异作用域为模块全部主码类；变异评分钉 100，pitest 任务独立红绿不接 check（与全仓口径一致）
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")
    // 本仓存在顶满 30s 的定时敏感用例，默认 1.2 倍超时窗会把慢用例误判为超时，放宽
    timeoutFactor.set(4.toBigDecimal())
    targetClasses.set(listOf("top.turboweb.struct.radix.*"))
    mutationThreshold.set(100)
}
