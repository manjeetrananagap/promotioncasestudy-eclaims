FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S eclaims && adduser -S eclaims -G eclaims
WORKDIR /app

# Copy pre-built JAR from Maven build step
COPY target/*.jar app.jar
RUN chown eclaims:eclaims app.jar
USER eclaims
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","app.jar"]
