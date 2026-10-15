/*
 * 插件与统一约定（java-library/maven-publish/jacoco、发布与测试机制）由根 build.gradle.kts 的 subprojects 块授予；
 * 异常类签名暴露 ConstraintViolation，校验 API 以 api 作用域供给；
 * 实现类自举引擎所需 Hibernate Validator 及消息插值的 EL 解析器以 implementation 作用域供给，不外泄。
 */
dependencies {
    api(libs.jakarta.validation.api)
    implementation(libs.hibernate.validator)
    implementation(libs.jakarta.el.api)
    implementation(libs.jakarta.el)
}

description = "turboweb-validation"