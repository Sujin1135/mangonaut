import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.autofixer"
version = "0.0.1-SNAPSHOT"
description = "mangonaut - AI Error Auto-Fixer"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

allprojects {
    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")

            debug {
                events("standardOut", "standardError")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    val kotestVersion = "6.0.0.M2"
    val mockkVersion = "1.14.2"

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-webflux")
        implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
        implementation("tools.jackson.module:jackson-module-kotlin")

        testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        // Kotest
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

        // MockK
        testImplementation("io.mockk:mockk:$mockkVersion")
    }

    // boot 모듈만 bootJar 활성화
    if (project.name != "boot") {
        tasks.getByName<BootJar>("bootJar") {
            enabled = false
        }
        tasks.getByName<Jar>("jar") {
            enabled = true
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// Root project는 빌드 결과물 비활성화
tasks.getByName<BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
