/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予；
 * 本模块零运行依赖：上下文组件契约与媒体类型值对象为本地实现，注解仅依赖 JDK 元注解。
 */
plugins {
    // 版本由根构建脚本的 plugins 块统一声明，此处按坐标启用
    id("info.solidsoft.pitest")
}

dependencies {
    // Gradle 9 不再自带 platform launcher；BOM 复用既有的 junit-jupiter 版本号，不引入第二个需要手工同步的版本
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

description = "turboweb-http-core"

// PITest 按先例在各自构建脚本自启（根脚本只对 commons 与 http-server 集中授予）：
// 变异作用域为模块全部主码类；变异门槛暂不钉线，先跑基线
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")
    // 本仓存在顶满 30s 的定时敏感用例，默认 1.2 倍超时窗会把慢用例误判为超时，放宽
    timeoutFactor.set(4.toBigDecimal())
    targetClasses.set(listOf("top.turboweb.http.core.*"))
}
