# db

ex-day の DB（PostgreSQL 18 ＋ PostGIS）のテーブル定義とサンプルデータ。設計は [docs/ex-day_db_design.md](../docs/ex-day_db_design.md) を参照。

| フォルダ | 内容 |
|---|---|
| `migration/` | テーブル定義（Flyway のマイグレーション。`V<番号>__<内容>.sql`） |
| `sample/` | 閲覧用のサンプルデータ（`R__sample_data.sql`）。ローカル環境だけで使う |
| `Dockerfile` | DB のイメージ（ex-day/poc と同じ構成） |

## 動かし方

リポジトリ直下で:

```bash
docker compose up
```

DB が起動したあと、`flyway` のコンテナがテーブルとサンプルデータを入れて終了する。手元からは `localhost:5433`（DB 名・ユーザー・パスワードはすべて `exday`）でつなげる。ポートは環境変数 `EXDAY_DB_PORT` で変えられる。

```bash
psql -h localhost -p 5433 -U exday exday
```

## テーブルやサンプルを変えるとき

- **テーブル**：`migration/` に新しい番号のファイルを足す。一度流したファイルは書き換えない。
- **サンプル**：`sample/R__sample_data.sql` を直して `docker compose up flyway` を実行すると、全部消して入れ直す。
- 最初から作り直したいとき：`docker compose down -v`（DB のデータも消える）。

## サンプルデータについて

会話や Discovery の内容は利用イメージであり、史実と異なる場合がある。写真は仮の画像、ニックネームは架空のもの。
