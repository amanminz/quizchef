# syntax=docker/dockerfile:1

# Build context is the repository root: the Gradle wrapper lives there
# and the backend is a multi-module Gradle build.

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so dependency resolution is cached
# until a build script changes.
COPY gradlew ./
COPY gradle/ gradle/
COPY backend/settings.gradle.kts backend/build.gradle.kts backend/
COPY backend/app/build.gradle.kts backend/app/
COPY backend/common/build.gradle.kts backend/common/
COPY backend/identity/build.gradle.kts backend/identity/
COPY backend/user/build.gradle.kts backend/user/
COPY backend/quiz/build.gradle.kts backend/quiz/
COPY backend/session/build.gradle.kts backend/session/
COPY backend/media/build.gradle.kts backend/media/
COPY backend/security/build.gradle.kts backend/security/
COPY backend/websocket/build.gradle.kts backend/websocket/
RUN ./gradlew -p backend :app:dependencies --no-daemon --quiet > /dev/null

COPY backend/ backend/
RUN ./gradlew -p backend :app:bootJar --no-daemon

# Split the boot jar into layers so unchanged dependencies stay cached.
RUN java -Djarmode=layertools -jar backend/app/build/libs/app-*.jar extract --destination extracted

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S quizchef && adduser -S quizchef -G quizchef

WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER quizchef
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 CMD wget -qO- "http://127.0.0.1:${PORT:-${SERVER_PORT:-8080}}/actuator/health/readiness" || exit 1
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-${SERVER_PORT:-8080}} org.springframework.boot.loader.launch.JarLauncher"]
