plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.dentalclinic"
version = "0.1.0-SNAPSHOT"
description = "Staff authentication and RBAC service (feature 001-staff-auth-rbac)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val javaTotpVersion = "1.7.1"
val awsSdkVersion = "2.29.0"
val testcontainersVersion = "1.20.4"

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:$awsSdkVersion")
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
    }
}

dependencies {
    // Core (T002)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.session:spring-session-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // MFA (T004)
    implementation("dev.samstevens.totp:totp:$javaTotpVersion")

    // AWS SDK v2 - KMS (T022a) and SES (PasswordResetService, research.md #10)
    implementation("software.amazon.awssdk:kms")
    implementation("software.amazon.awssdk:ses")

    // Test (T007)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Linting/formatting (T005)
checkstyle {
    toolVersion = "10.20.2"
    configFile = file("checkstyle.xml")
}

spotless {
    java {
        // Pinned above the Spotless-bundled default (1.24.0), which predates JDK 25 support:
        // google-java-format < 1.34.0 throws NoSuchMethodError against JDK 25's javac
        // (Log$DeferredDiagnosticHandler.getDiagnostics() changed Queue -> List).
        googleJavaFormat("1.36.1")
        removeUnusedImports()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
