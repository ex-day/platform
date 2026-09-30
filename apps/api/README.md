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

## 「今」と「いる場所」の設定

推薦は「今」と「いる場所」を軸にする。開発中は、この2つを設定で固定して画面を確かめられる。設定を空にすると、実際の日時（Asia/Tokyo）と既定エリア（桜木町駅の付近）を使う。使ってみる人の現在地は API で受け取らない（[#89](https://github.com/ex-day/platform/issues/89) の決定）。

設定は Spring の設定プロパティで指定する。環境変数や `application.properties`、`application.yaml` から渡せる。

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `exday.dev.clock.now` | （空）＝実時計 | 「今」を固定する ISO 8601 の日時。例：`2026-04-02T15:00:00+09:00` |
| `exday.dev.location.name` | （空）＝既定エリア | 「いる場所」の表示名。`lat`・`lng` と合わせて設定する |
| `exday.dev.location.lat` | （空） | 緯度 |
| `exday.dev.location.lng` | （空） | 経度 |
| `exday.dev.default-location.name` | `桜木町駅` | 「いる場所」が未指定のときの既定エリアの表示名 |
| `exday.dev.default-location.lat` | `35.4509` | 既定エリアの緯度 |
| `exday.dev.default-location.lng` | `139.6309` | 既定エリアの経度 |

`clock.now` が空なら `context.clockSource` は `system`、指定していれば `fixed` を返す。`location.*` が空なら `context.locationSource` は `default`、指定していれば `fixed` を返す。季節は「今」の月から決める（3〜5月：spring、6〜8月：summer、9〜11月：autumn、12〜2月：winter。タイムゾーンは Asia/Tokyo）。

例：4月2日の15時に桜木町駅の前にいるとして起動する。

```bash
EXDAY_DEV_CLOCK_NOW=2026-04-02T15:00:00+09:00 \
EXDAY_DEV_LOCATION_NAME=桜木町駅 \
EXDAY_DEV_LOCATION_LAT=35.4509 \
EXDAY_DEV_LOCATION_LNG=139.6309 \
./gradlew bootRun
```

## OpenAPI からの生成

API のインターフェース（Spring の interface）と、モデル（DTO）は [`docs/api/openapi.yaml`](../../docs/api/openapi.yaml) から生成する。[openapi-generator-gradle-plugin](https://github.com/OpenAPITools/openapi-generator/tree/master/modules/openapi-generator-gradle-plugin) を使う。生成物は `build/generated/openapi/` 配下に置き、Git には commit しない（ビルドのたびに生成する）。

- 生成タスク：`./gradlew openApiGenerate`。`compileJava` が依存するため、通常のビルド（`./gradlew build`）で自動的に流れる。
- コントローラーは、生成された interface（例：`io.github.exday.api.generated.api.S01Api`）を実装する。OpenAPI と実装が食い違うとコンパイルで分かる。
- 生成された interface を実装するコントローラー Bean を登録しているエンドポイントについては、interface の未実装メソッド（コントローラーで override していないメソッド）が `default` 実装で `501 Not Implemented` を返す。コントローラー Bean そのものを登録していない interface のエンドポイントは、ルーティング自体が登録されないため `404 Not Found` になる（例：この Issue 時点では `S01Api` のみ実装しているため、`S03Api` のパスは 404 になる）。

## パッケージ名

`io.github.exday.api` を仮に使っている。別の案があれば PR で提案してほしい。
