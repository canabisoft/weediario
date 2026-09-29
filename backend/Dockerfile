# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S weediario && adduser -S -G weediario -H -s /sbin/nologin weediario \
    && mkdir -p /app/data/images \
    && chown -R weediario:weediario /app/data \
    && chmod 700 /app/data
WORKDIR /app
COPY --from=build --chown=weediario:weediario /build/target/weediario-backend-*.jar app.jar
USER weediario:weediario
EXPOSE 8080
# Rota pública e barata que também exercita o banco. start-period cobre as migrations na primeira subida.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q --spider "http://127.0.0.1:8080/api/v1/feed/diaries?size=1" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
