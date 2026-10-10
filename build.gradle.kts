/*
 * 全部子项目的统一构建约定：子项目脚本不再声明插件，Java 库身份、公共依赖、
 * 发布与测试/覆盖率机制由本脚本 subprojects 块统一授予；
 * 覆盖率阈值与 check 门禁绑定留在各模块自身脚本（turboweb-commons、turboweb-http-server）。
 */

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

    dependencies {
        add("api", "io.projectreactor.netty:reactor-netty:1.3.5")
        add("api", "org.apache.httpcomponents.core5:httpcore5:5.3.1")
    }

    group = "io.gitee.turboweb"
    version = "2.4.0-Beta2"

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
}
