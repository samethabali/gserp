# syntax=docker/dockerfile:1.7

# ───────── Build stage ─────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependency cache layer: önce pom, sonra src — pom değişmediğinde
# dependency download cache'i yeniden kullanılır.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package \
    && mv target/*.jar /workspace/app.jar

# ───────── Runtime stage ─────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user
RUN addgroup -S gserp && adduser -S -G gserp -h /app gserp

WORKDIR /app

# curl: healthcheck için; gerisi minimal kalır
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Europe/Istanbul /etc/localtime \
    && echo "Europe/Istanbul" > /etc/timezone

COPY --from=build /workspace/app.jar app.jar

# Log dizini (prod logback bunu kullanır), gserp'e ait
RUN mkdir -p /var/log/gserp && chown -R gserp:gserp /app /var/log/gserp

USER gserp

EXPOSE 8989

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8989 \
    LOG_DIR=/var/log/gserp

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8989/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
