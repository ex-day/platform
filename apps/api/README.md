# apps/api

ex-day の API サーバー（Spring Boot、Java 25、Gradle Kotlin DSL）の土台。Issue [ex-day/platform#99](https://github.com/ex-day/platform/issues/99) で作った。

S01 のセクション一覧と、各セクションの推薦カード、S03 の Discovery 詳細を返す API を実装している。

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
- 生成された interface を実装するコントローラー Bean を登録しているエンドポイントについては、interface の未実装メソッド（コントローラーで override していないメソッド）が `default` 実装で `501 Not Implemented` を返す。コントローラー Bean そのものを登録していない interface のエンドポイントは、ルーティング自体が登録されないため `404 Not Found` になる（例：`S03Api` のうち `GET /discoveries/{discoveryId}` のみ実装しているため、`GET /discoveries/{discoveryId}/posts` は 501 になる）。

## パッケージ名

`io.github.exday.api` を仮に使っている。別の案があれば PR で提案してほしい。

## セクションの推薦カード

`GET /api/v1/discovery-sections/{sectionKey}/discoveries?limit=8` でカードを取得する。
`sectionKey` は `nearby` または `season`、`limit` は 1〜20（省略時は8）。
未知の key は404、不正な limit は400を `application/problem+json` で返す。

- `nearby`：設定の場所から `exday.recommendation.nearby-radius-meters` メートル以内（既定3000、正の値）の代表の場所を持ち、季節が一致・通年・季節条件なしの Value。
- `season`：今の季節と一致する Value。距離の上限は設けない。
- 成立済み・公開中の Discovery だけが対象。同じ Discovery の異なる Value は別カードとして返す。
- 仮の評価として季節一致を優先し、その中で代表の場所への距離順に並べてから limit を適用する。同点は公開ID順で固定する。評価は `DiscoveryRecommendation` に分離している。

DB を使う API テストは `db/Dockerfile` とマイグレーション・サンプルデータを使い、春／秋、距離制限、公開状態、カードの内容とエラー応答を検証する。

## Discovery 詳細

`GET /api/v1/discoveries/{discoveryId}` で1件の Discovery の詳細を返す。成立済み・公開中でなければ 404（`application/problem+json`）を返す。

- Value（`recommendations`／`otherValues`）：今の季節に一致する・通年・季節条件なしの Value を `recommendations`、季節が合わない Value を `otherValues` に振り分ける。
- Findings（`findings`）：`backedBy`（出典）があるものは反応集計（`reactions`）を含めず、ないものだけ種別（`value`／`theory`）に応じた反応（understand／wantToGo、または maybe）を返す。
- タグ（`tags`）：投稿由来のタグと運営が付けたタグを合わせて重複なく返す。
- 反応（`reactions`）：Discovery 全体への like／surprised／love の集計。
- 関連（`relations`）：派生元（`derivedFrom`）・派生先（`derivedTo`）・関連（`related`）の3つに分け、それぞれ相手 Discovery の一番の Value（推薦度順の先頭）と代表の場所・画像でカードを組み立てる。

DB を使う API テストは、可視性を書き換える 404 のケースがあるためテストごとにサンプルデータを再投入し、春／秋の振り分け、派生元・関連のカード組み立て、404 応答を検証する。
