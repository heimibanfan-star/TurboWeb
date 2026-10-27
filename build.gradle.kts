/*
 * 全部子项目的统一构建约定：子项目脚本不再声明插件，Java 库身份、
 * 发布与测试/覆盖率机制由本脚本 subprojects 块统一授予；
 * 第三方依赖不在本脚本全局引入，由各模块自身脚本按真实用量声明；
 * 覆盖率阈值与 check 门禁绑定留在各模块自身脚本（turboweb-common、turboweb-server）。
 */

plugins {
    // 变异测试插件：此处只定版本与类路径，下方 subprojects 对有测试源码的模块授予
    id("info.solidsoft.pitest") version "1.19.0" apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "jacoco")

    repositories {
        mavenLocal()
        maven {
            url = uri("https://repo.maven.apache.org/maven2/")
        }
    }

    group = "io.gitee.turboweb"
    version = "2.6.0-SNAPSHOT"

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
    }

    configure<PublishingExtension> {
        publications.create<MavenPublication>("maven") {
            from(components["java"])
        }

        repositories {
            maven {
                name = "aliyunRDC"
                url = uri("https://packages.aliyun.com/69cb35b37c12f71e8ca5b2be/maven/luanet")

                credentials {
                    username = System.getenv("ALIYUN_REPO_USERNAME")
                    password = System.getenv("ALIYUN_REPO_PASSWORD")
                }
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }

    // ---------- 测试与覆盖率机制（机制在本脚本，阈值与门禁绑定在各模块） ----------
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // L3 端到端用例命名 *IT，Gradle 默认 include 不含它，必须显式加入
        include("**/*Test.class", "**/*IT.class")
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<org.gradle.testing.jacoco.tasks.JacocoReport>().configureEach {
        // 无测试的模块不能让 finalize 失败：没有执行数据就跳过报告生成。
        // 守卫读 layout provider 而不是任务的 executionData，避免把任务引用序列化进 configuration-cache
        val execFile = layout.buildDirectory.file("jacoco/test.exec")
        onlyIf { execFile.get().asFile.exists() }
        reports {
            xml.required = true
            html.required = true
        }
    }

    // ---------- 变异测试（PITest）：机制在此授予，作用域（targetClasses）留在各模块脚本 ----------
    // 仅 commons 与 server 在此集中授予；其余有测试源码的模块（turboweb-struct、turboweb-serializer、turboweb-rest-core）
    // 按先例在各自构建脚本自启 pitest，本列表不再扩充。
    if (name in setOf("turboweb-common", "turboweb-server")) {
        apply(plugin = "info.solidsoft.pitest")
        configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
            // JUnit5 provider 由 pitest-junit5-plugin 供给了引擎自动发现；
            // 废弃的 testPlugin 属性不再设置（PIT 1.6.7 起引擎侧忽略该值）
            pitestVersion.set("1.30.0")
            junit5PluginVersion.set("1.2.3")
            // 本仓存在顶满 30s 的定时敏感用例，默认 1.2 倍超时窗会把慢用例误判为超时，放宽
            timeoutFactor.set(java.math.BigDecimal.valueOf(4))
            // mutationThreshold 暂不钉线：先跑基线，随用例补充逐步抬升
        }
    }
}
