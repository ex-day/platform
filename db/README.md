# db

ex-day の DB（PostgreSQL 18 ＋ PostGIS）のテーブル定義とサンプルデータ。設計は [docs/ex-day_db_design.md](../docs/ex-day_db_design.md)（v0.2）を参照。

`V2__recreate_tables_for_mvp.sql` で、V1（閲覧用）の表をすべて消し、MVP の表に作り直した（Issue #135）。API（`apps/api`）・画面（`apps/web`）は V1 の表を読んでいるので、新しい表に合わせるまで動かない。

| フォルダ | 内容 |
|---|---|
| `migration/` | テーブル定義（Flyway のマイグレーション。`V<番号>__<内容>.sql`） |
| `sample/` | サンプルデータ（`R__sample_data.sql`）。ローカル環境だけで使う |
| `check/` | 制約・ビューの確認の SQL と手順（[check/README.md](check/README.md)）。Flyway では流さない |
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

手元に `psql` がない場合は、DB コンテナから入る:

```bash
docker compose exec db psql -U exday exday
```

## テーブルやサンプルを変えるとき

- **テーブル**：`migration/` に新しい番号のファイルを足す。一度流したファイルは書き換えない。
- **サンプル**：`sample/R__sample_data.sql` を直して `docker compose up flyway` を実行すると、全部消して入れ直す。
- 最初から作り直したいとき：`docker compose down -v`（DB のデータも消える）。

## サンプルデータについて

会話・Discovery・企画の内容は利用イメージであり、史実と異なる場合がある。場所の位置・形はおおよそで、オープンデータから取り込んだものではない。坂の勾配などの推定の値は仮の値。ニックネームは架空のもの。中身はファイルの冒頭の説明を参照。

一般のユーザー向けに読むときは、公開のものだけを返すビュー（`visible_*`）を使う。表を直接読むのは、運営・本人向けの読み取りで、権限を API で確かめたときだけにする（DB 設計 2.5節）。
