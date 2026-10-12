/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予
 */

dependencies {
    // 本模块只消费 netty 的内部日志门面（InternalLogger），netty-common 即够，不背 codec-http 编解码整包
    implementation(libs.netty.common)
    // 线程工厂签名上的 NonNull 注解仅编译期需要：compileOnly 供给，不入 POM 与运行时类路径
    compileOnly(libs.jspecify)

    // Gradle 9 不再自带 platform launcher；BOM 复用既有的 junit-jupiter 版本，不引入第二个版本号
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

description = "turboweb-concurrent"
