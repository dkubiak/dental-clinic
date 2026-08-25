plugins {
    // Lets Gradle auto-download the JDK 25 toolchain (build.gradle.kts) if it isn't already
    // installed locally — so `./gradlew` needs nothing pre-installed but the wrapper itself.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "patient-service"
