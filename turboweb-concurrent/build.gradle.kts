/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予
 */
plugins {
    // 版本由根构建脚本的 plugins 块统一声明，此处按坐标启用（与 turboweb-struct 先例一致）
    id("info.solidsoft.pitest")
}

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

// 覆盖率闸：作用域仅 VirtualThreadMicroTaskExecutor 抽象基类及其私有嵌套的受限/非受限两实现
// （实现已内聚为基类的私有内部类，类名即 VirtualThreadMicroTaskExecutor$Limited / $Unlimited）。
// 基类单列一条规则：线程启动失败补偿的 catch 三行（归还、重抛）测试不可达
// （start() 无法按需失败，类保持 final 不设注入缝，属已裁决口径），线覆盖下限 0.70；
// 两实现维持 LINE+BRANCH 双 1.00 不放松。既有 ThreadPool/ElasticThreadPool 维持无闸口径不扩面
tasks.named<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf("*.VirtualThreadMicroTaskExecutor*")
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.00".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("*VirtualThreadMicroTaskExecutor\$Limited*", "*VirtualThreadMicroTaskExecutor\$Unlimited*")
            limit {
                counter = "LINE"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// 变异作用域同样只钉这套类（基类、私有嵌套的两实现）；
// 阈值按"可达变异全杀"设定：实测 50 枚变异全部击杀、Test strength 100%（无 no-coverage），
// 阈值钉 94 作余量，留出以后补用例时的浮动空间。pitest 任务独立红绿不接 check（与全仓口径一致）
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")
    // 本仓存在顶满超时的并发用例，默认 1.2 倍超时窗会误判，放宽
    timeoutFactor.set(4.toBigDecimal())
    targetClasses.set(listOf(
        "top.turboweb.concurrent.thread.*VirtualThreadMicroTaskExecutor*",
    ))
    // 实测全杀（50/50、100%），钉 94 留余量
    mutationThreshold.set(94)
}
