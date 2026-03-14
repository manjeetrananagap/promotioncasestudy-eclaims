FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S eclaims && adduser -S eclaims -G eclaims
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown eclaims:eclaims app.jar
USER eclaims
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","app.jar"]
