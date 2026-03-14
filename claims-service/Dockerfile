# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Cache dependencies first
COPY pom.xml .
COPY ../pom.xml ../pom.xml 2>/dev/null || true

# Copy source
COPY src ./src

RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime (JRE only ~80MB vs JDK ~300MB) ─────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S eclaims && adduser -S eclaims -G eclaims
WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown eclaims:eclaims app.jar
USER eclaims

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
