/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予；
 * 本模块为契约包：ServerRuntime 的装配签名只暴露 netty 的 ChannelPipeline，不背 codec-http 编解码整包。
 */

dependencies {
    // ChannelPipeline 出现在接口签名上，以 api 对外暴露：契约消费方经传递依赖拿到该类型
    api(libs.netty.transport)
}

description = "turboweb-server-runtime"
