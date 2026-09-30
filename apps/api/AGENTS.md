# apps/api の作業ルール

このディレクトリは ex-day の API（バックエンド）を置く場所である。ここで作業するエージェントは、ルートの `AGENTS.md` と `docs/development/ai-collaboration.md` に加えて、以下の書き方に従う。ルールの根拠は Issue #89 の「API の土台の技術（決定）」および関連コメントにある。矛盾がある場合は作業を止めて人間に確認する。

## 使う技術

| 項目 | 決定 |
|---|---|
| 言語 | Java 25（LTS） |
| フレームワーク | Spring Boot 4.1 系（最新の安定版） |
| ビルド | Gradle（Kotlin DSL） |
| DB アクセス | Spring の `JdbcClient`（JPA・MyBatis は使わない） |
| テスト | JUnit ＋ Testcontainers（`db/Dockerfile` と同じ構成の PostgreSQL＋PostGIS） |
| Spring Security | 認証（#75）の実装まで**入れない** |

万一動かない部品があれば Java 21（LTS）に下げる（Gradle の指定 1 行）。判断は人間に確認する。

## DB アクセスの書き方

- DB アクセスは Spring の `JdbcClient` を使う。JPA・MyBatis は使わない。
- SQL は Java のテキストブロック（`"""..."""`）で書く。文字列の連結はしない。
- SQL は**クエリ用のクラスの先頭に定数としてまとめる**。メソッドの中はパラメータの受け渡しと結果の詰め替えだけにする。
- **値は必ずパラメータで渡す**。SQL の文字列に値を埋め込まない（SQL の解析・実行計画のキャッシュを効かせ、SQL インジェクションを防ぐ）。
- PostGIS 等の型は、できるだけ SQL の側で数値・文字列に変えて返す（アプリ側で型の対応を持たなくて済むようにする）。

## マイグレーション

- API の起動時にマイグレーションを流さない。
- `db/` のマイグレーションは `compose.yaml` の flyway サービスで流す。

## テスト

- DB を使うテストは Testcontainers で書く。使う PostgreSQL＋PostGIS は `db/Dockerfile` と同じ構成にする（本番と同じ拡張で SQL の正しさを確かめられるようにする）。

## Spring の使い方

- Spring の暗黙の仕組みを使いすぎない。依存はコンストラクタで渡す等、素直な書き方に寄せる。

## Spring Security

- 認証（#75）の実装まで**入れない**。
- 理由：今回の API はログインなしの読み取りだけ。入れると全 API が既定で認証必須になり、中身のない許可の設定だけが増える。
- いずれ入れるときは、外部の認証サービスのトークンを検証する部品（OAuth2 Resource Server）として使う。

## その他

- OpenAPI（`docs/api/openapi.yaml`）を約束事の正とする。API 側のインターフェースと、画面側（TypeScript）の型は、OpenAPI から生成する方針（#89）。
- 「今」と「いる場所」は開発用の設定ファイルで指定できるようにする（#89 のコメント参照）。値は API 側で読む。
