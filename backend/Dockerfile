# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e

FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# Ubuntu (glibc) e não Alpine: o encoder WebP (libwebp via JNI) é compilado para glibc e não carrega no musl.
FROM eclipse-temurin:21-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c
# uid/gid 100/101 são os que o adduser do Alpine gerava: volumes criados pela imagem antiga continuam acessíveis.
RUN groupadd --system --gid 101 weediario \
    && useradd --system --uid 100 --gid weediario --no-create-home --shell /usr/sbin/nologin weediario \
    && mkdir -p /app/data/images /app/native \
    && chown -R weediario:weediario /app/data /app/native \
    && chmod 700 /app/data /app/native
WORKDIR /app
COPY --from=build --chown=weediario:weediario /build/target/weediario-backend-*.jar app.jar
USER weediario:weediario
EXPOSE 8080
# Rota pública e barata que também exercita o banco. start-period cobre as migrations na primeira subida.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:8080/api/v1/feed/diaries?size=1" || exit 1
# O encoder WebP extrai a libwebp para /app/native antes de carregá-la: esse diretório precisa permitir exec,
# ao contrário do /tmp (tmpfs noexec no compose).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", \
            "-Dcom.luciad.imageio.webp.tmpdir=/app/native", "-jar", "/app/app.jar"]
