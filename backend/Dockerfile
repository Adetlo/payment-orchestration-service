# syntax=docker/dockerfile:1

# ---- build stage: resolve dependencies first so they are cached as their own layer ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY .mvn ./.mvn
RUN mvn -q -B -e dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests -Dspotless.check.skip=true -Dspotbugs.skip=true -Djacoco.skip=true package \
    && cp target/payment-orchestration-service-*.jar /workspace/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 -S app && adduser -u 10001 -S -G app -h /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=docker
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
