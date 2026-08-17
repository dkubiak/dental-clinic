# syntax=docker/dockerfile:1

# Build stage — matches the JDK 25 toolchain pinned in build.gradle.kts, so no separate toolchain
# download is needed at build time.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts checkstyle.xml ./
COPY gradle gradle
COPY src src
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN useradd --system spring
USER spring
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
