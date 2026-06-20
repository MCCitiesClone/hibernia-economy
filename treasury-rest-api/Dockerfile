# ── Stage 1: Build Java ────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Cache Gradle wrapper & dependencies
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy source and build the fat jar
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:21-jre

# Security: run as non-root
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /workspace/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

# Default Spring profile – override at runtime via SPRING_PROFILES_ACTIVE
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
