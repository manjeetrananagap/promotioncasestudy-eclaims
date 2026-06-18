FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S eclaims && adduser -S eclaims -G eclaims
WORKDIR /app

# Copy pre-built JAR from Maven build step
COPY target/*.jar /tmp/jars/
RUN set -eux; \
  JAR="$(ls /tmp/jars/*.jar | grep -v 'original' | head -n 1)"; \
  cp "$JAR" /app/app.jar; \
  chown eclaims:eclaims /app/app.jar
USER eclaims

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
