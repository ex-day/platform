# ex-day の API のイメージ（Issue #114）
#
# ビルドの context はリポジトリ直下にする（OpenAPI からの生成で docs/api/openapi.yaml を読むため）。
#   docker build -f apps/api/Dockerfile .
# テストは Docker（Testcontainers）が要るため、ここでは流さない（CI の ./gradlew build で流す）。

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace/apps/api

# 依存の取得を先に済ませ、ソースだけ変えたときはキャッシュを使う
COPY apps/api/gradlew apps/api/settings.gradle.kts apps/api/build.gradle.kts ./
COPY apps/api/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY docs/api/openapi.yaml /workspace/docs/api/openapi.yaml
COPY apps/api/src ./src
RUN ./gradlew --no-daemon bootJar \
    && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 exday
COPY --from=build /workspace/app.jar ./app.jar
USER exday
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
