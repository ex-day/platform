# apps/api

ex-day の API サーバー（Spring Boot、Java 25、Gradle Kotlin DSL）の土台。Issue [ex-day/platform#99](https://github.com/ex-day/platform/issues/99) で作った。

現時点では API のエンドポイントは無く、**ビルドと、DB につないだテストが Java 25 で通ること**までを目的とする。エンドポイントは後続の Issue で足す。

## 使う技術

- Java 25（Gradle の toolchain で指定）
- Gradle（Kotlin DSL、Gradle Wrapper 付き）
- Spring Boot 4.1 系
- Spring Web（MVC）＋ Spring JDBC の `JdbcClient`（JPA・MyBatis は使わない）
- PostgreSQL の JDBC ドライバ
- テスト：JUnit ＋ Testcontainers ＋ Flyway（テストの中でだけ流す）

Spring Security は今は入れない（認証は [#75](https://github.com/ex-day/platform/issues/75) で入れる）。

## SQL の書き方

- クエリはクラスの先頭に `static final String` の定数としてまとめる。テキストブロック（`"""`）で書く。
- 値は必ずパラメータで渡し、SQL の文字列に埋め込まない。
- 例：[`src/test/java/io/github/exday/api/SampleQueries.java`](src/test/java/io/github/exday/api/SampleQueries.java)。

## ビルド

Java 25 の JDK が必要。手元では [SDKMAN!](https://sdkman.io/) 等で Temurin 25 を入れておく。

```bash
./gradlew build
```

Gradle の toolchain で Java 25 を選ぶため、`JAVA_HOME` が別の版でもビルドできる（見つからない場合は Gradle が自動で取得する）。

## テスト

```bash
./gradlew test
```

Testcontainers が Docker を使うため、Docker Engine が動いている必要がある。テストは次の手順で走る。

1. リポジトリの [`db/Dockerfile`](../../db/Dockerfile) から DB のイメージ（PostgreSQL 18 ＋ PostGIS ＋ pgvector）をビルドする。
2. そのイメージで PostgreSQL コンテナを起動する。
3. [`db/migration/`](../../db/migration/) と [`db/sample/`](../../db/sample/) を Flyway の Java API で流す。
4. `JdbcClient` でサンプルデータを読み、公開中の Discovery の件数と、PostGIS の関数（`ST_Distance`）が使えることを確かめる。

本番・ローカル（`docker compose up`）では、これまでどおり compose の flyway コンテナがマイグレーションを流す。API の起動時にはマイグレーションを流さない（`spring.flyway.enabled=false`）。

## 起動

前提として、リポジトリ直下で `docker compose up` により DB が起動していること。

```bash
./gradlew bootRun
```

DB の接続先は環境変数で上書きできる。既定値は [compose.yaml](../../compose.yaml) の DB。

| 環境変数 | 既定値 |
| --- | --- |
| `EXDAY_DB_URL` | `jdbc:postgresql://localhost:5433/exday` |
| `EXDAY_DB_USER` | `exday` |
| `EXDAY_DB_PASSWORD` | `exday` |

例：

```bash
EXDAY_DB_URL=jdbc:postgresql://localhost:5433/exday \
EXDAY_DB_USER=exday \
EXDAY_DB_PASSWORD=exday \
./gradlew bootRun
```

## パッケージ名

`io.github.exday.api` を仮に使っている。別の案があれば PR で提案してほしい。
