/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予
 */
plugins {
    // 版本由根构建脚本的 plugins 块统一声明，此处按坐标启用
    id("info.solidsoft.pitest")
}

dependencies {
    // JSON 序列化直接落在 jackson 上：ObjectMapper/Module/TypeReference 进入构造签名与返回值，api 暴露入 POM 与消费侧编译类路径
    api(libs.jackson.databind)
    // Java 时间模块（JavaTimeModule）只在构造内部装配、不出现在签名，implementation 即够
    implementation(libs.jackson.datatype.jsr310)

    // Gradle 9 不再自带 platform launcher；BOM 复用既有的 junit-jupiter 版本，不引入第二个需要手工同步的版本号
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

description = "turboweb-serializer"

// 覆盖率闸：分母即模块全部主码类（接口无可执行行，不计入），线覆盖钉 1.00。
// 主码无 if/switch 条件分支，分支计数器为空时 BRANCH 限制按无违例处理；
// 异常流向（try 正常路 / catch 包装路）由双向钉住的用例覆盖，INSTRUCTION 钉 1.00 兜住整条指令边。
tasks.named<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "INSTRUCTION"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// 变异作用域为模块全部主码类（含 exception 子包）；评分用全仓统一的缺省变异算子组，
// 口径与 commons、server、struct 可比。pitest 任务独立红绿不接 check。
// 评分钉 100：缺省组 12 个变异体全杀。曾以 NON_VOID_METHOD_CALLS 扩展探针复核装配行强度，
// 结果 39/42，三个幸存体均为不可杀的等价变异体（LocalDate 的 yyyy-MM-dd 模式与 ISO_LOCAL_DATE
// 可接受集逐输入一致；Instant 两行重复注册 JavaTimeModule 自带默认），非用例缺口，故扩展组不入闸。
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    pitestVersion.set("1.30.0")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(listOf("top.turboweb.serializer.*"))
    mutationThreshold.set(100)
}
