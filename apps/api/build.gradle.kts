plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.10.0"
}

group = "io.github.exday"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// OpenAPI からの生成設定。
// 生成物は build/ 配下に置き、commit しない。sourceSets へ足してコンパイル対象にする。
// 実装するのは interface のみ（interfaceOnly=true）、コントローラーは別途手で書いて実装する。
val openApiSpec = layout.projectDirectory.file("../../docs/api/openapi.yaml")
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(openApiSpec.asFile.absolutePath)
    outputDir.set(openApiOutputDir.get().asFile.absolutePath)
    apiPackage.set("io.github.exday.api.generated.api")
    modelPackage.set("io.github.exday.api.generated.model")
    // 生成された API 情報の Bean（コントローラーやハンドラー）は作らない。interface だけ使う。
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useJakartaEe" to "true",
            "useTags" to "true",
            "openApiNullable" to "false",
            "useBeanValidation" to "true",
            // 任意項目（写真など）は値がなければ JSON から省く。
            "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)",
            "dateLibrary" to "java8",
            "hideGenerationTimestamp" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "skipDefaultInterface" to "false",
        )
    )
    // モデル・API 以外のドキュメントやサポートファイルは要らない。
    globalProperties.set(
        mapOf(
            "apis" to "",
            "models" to "",
            "supportingFiles" to "ApiUtil.java",
            "modelDocs" to "false",
            "apiDocs" to "false",
            "modelTests" to "false",
            "apiTests" to "false",
        )
    )
}

sourceSets {
    named("main") {
        java.srcDir(openApiOutputDir.map { it.dir("src/main/java") })
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
