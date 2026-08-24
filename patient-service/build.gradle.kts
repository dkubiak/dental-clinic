plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.dentalclinic"
version = "0.1.0-SNAPSHOT"
description = "Patient records service (feature 002-patient-records)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val testcontainersVersion = "1.20.4"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    }
}

dependencies {
    // Core — mirrors backend/build.gradle.kts minus AWS SDK/java-totp (T001, plan.md Primary
    // Dependencies): no MFA/email work happens in this service.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.session:spring-session-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

checkstyle {
    toolVersion = "10.20.2"
    configFile = file("checkstyle.xml")
}

spotless {
    java {
        googleJavaFormat("1.36.1")
        removeUnusedImports()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
