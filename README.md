# ex-day Platform

> 予定された一日に、もう一つの発見を。

**ex-day** は、現在地・時間・空き時間などのコンテキストをもとに、普段なら気づかなかった場所、出来事、地域の知識や疑問との偶発的な出会いをつくるための行動・発見支援プラットフォームです。

このリポジトリでは、完成したサービスだけではなく、アイデアを要求に落とし、MVPを定義し、UI/UXを設計し、実装していくまでの過程も公開していきます。

## Why ex-day?

目的地や予定を検索するサービスは数多くあります。

一方で、日常には「少し時間が空いた」「いつもと違う道を歩いてみたい」「近くに何か面白いものはないだろうか」といった、まだ目的そのものが決まっていない時間があります。

ex-dayでは、ユーザーがすでに知っているものを探すだけではなく、**今いる場所や時間をきっかけに、知らなかった何かを発見できる体験**をつくることを目指しています。

## Current Status

🚧 **Planning / MVP Design**

現在はMVPの設計・プロトタイプ作成フェーズです。

現在進めていること：

- 要求整理
- MVPスコープ定義
- 画面一覧・画面遷移設計
- UIコンポーネント定義
- ワイヤーフレーム設計
- Web / APIの技術構成検討

まだ仕様や技術構成は確定していません。

設計・検討の過程そのものを残しながら、MVP実装へ進めています。

## ローカルで動かす

リポジトリを clone して `docker compose up` すると、DB・API・画面が起動し、S01（おすすめの一覧）と S03（Discovery の詳細）を手元で触れます。Docker（Docker Compose v2）が必要です。

> 画面に出るデータは、利用のイメージを伝えるためのサンプルです。史実や実際の場所・出来事と異なる場合があります。

### 起動する

```bash
docker compose up
```

初回はイメージのビルドに数分かかります。起動したら、ブラウザで <http://localhost:3000> を開きます。S01 のカードを押すと S03 に移ります。

起動するもの（[compose.yaml](compose.yaml)）：

| サービス | 内容 | 手元のポート（既定） |
| --- | --- | --- |
| `db` | PostgreSQL ＋ PostGIS（[db/](db/)） | `127.0.0.1:5433` |
| `flyway` | テーブルとサンプルデータを入れて終わる | なし |
| `api` | API（[apps/api/](apps/api/)）。`flyway` の完了を待って起動する | `127.0.0.1:8080`（`/api/v1`） |
| `web` | 画面（[apps/web/](apps/web/)）。API はコンテナの中から `http://api:8080/api/v1` で呼ぶ | `127.0.0.1:3000` |

ほかのプロジェクトとポートがぶつかる場合は、環境変数で変えられます。

```bash
EXDAY_WEB_PORT=13000 EXDAY_API_PORT=18080 EXDAY_DB_PORT=15433 docker compose up
```

### 「今」と「いる場所」を変える

おすすめは「今」と「いる場所」をもとに決まります。何も設定しなければ、実際の日時（日本時間）と既定の場所（桜木町駅の付近）を使います。

開発中に固定したい場合は、[dev/exday-dev.yaml](dev/exday-dev.yaml) の例の `#` を外して値を書き、API を起動し直します。

```yaml
exday:
  dev:
    clock:
      now: "2026-04-02T15:00:00+09:00"
    location:
      name: 桜木町駅
      lat: 35.4509
      lng: 139.6309
```

```bash
docker compose restart api
```

ブラウザを再読み込みすると、S01 の「今の時期」が変わります（例：4月なら春の Discovery）。設定の意味は [apps/api/README.md](apps/api/README.md) の「「今」と「いる場所」の設定」を参照してください。試したあとは、ファイルを元に戻してから commit してください。

### 止める・作り直す

```bash
# 止める（Ctrl+C でも止まる）。DB のデータは残る
docker compose down

# ソースを変えたあと、イメージを作り直して起動する
docker compose up --build

# DB のデータも消して、何もない状態から作り直す
docker compose down -v
docker compose up --build
```

## Join the Project

ex-dayは現在、個人で立ち上げている実験的なプロジェクトです。

ただし、最終的に一人ですべてを作ることを目的にはしていません。

このアイデアに興味を持った人が、それぞれの得意分野から参加し、一緒に形にしていけるプロジェクトにしたいと考えています。

例えば、こんな分野に興味がある方との協力を歓迎します。

- UI / UX
- Webフロントエンド
- Backend / API
- Cloud / Infrastructure
- AI / Recommendation
- 地図・位置情報
- 地域情報・歴史・イベントなどのコンテンツ
- サービス企画・プロダクト設計

「実装はしないけれどアイデアには興味がある」「地域情報について協力できる」といった関わり方も歓迎します。

声をかけてもらうときは、[Discussions の案内の投稿](https://github.com/orgs/ex-day/discussions/1)へのコメントや、新しい Discussion で気軽にどうぞ。

参加方法やContributionのルールについては、プロジェクトの進行に合わせて整備していく予定です。ライセンスと将来の方針は、下の [License / Use of Ideas](#license--use-of-ideas) を見てください。

## Project Philosophy

ex-dayでは、サービスそのものと同時に、**アイデアを公開し、共感する人と一緒に形にしていくプロセス**も一つの実験だと考えています。

アイデアを考える人、設計する人、実装する人、運営する人は、必ずしも同じ人である必要はありません。

公開されたアイデアや成果が別のプロジェクトにつながったり、誰かの活動のヒントになったりすることも歓迎します。

その際、可能であれば ex-day や元になった活動への言及・クレジットを残してもらえるとうれしいです。

また、このリポジトリでの設計・開発・Contributionが、参加した人自身の実績やポートフォリオとして残るようなプロジェクトを目指します。

## Repository

このリポジトリは、ex-dayの中核となるWebプラットフォームを管理するmonorepoです。

```text
platform/
├── docs/
│   ├── requirements.md
│   ├── mvp-scope.md
│   ├── screen-list.md
│   ├── screen-transition.md
│   └── wireframe-spec.md
│
├── apps/
│   ├── web/
│   └── api/
│
└── README.md
```

### `docs`

要求、MVPスコープ、画面設計、UI/UX設計など、サービスを形にしていく過程のドキュメントを管理します。

### `apps/web`

Webフロントエンドを配置します。

現時点では **Next.js / React** を候補としています。

### `apps/api`

ex-dayのAPIを配置します。

現時点では **Spring Boot** を第一候補としていますが、MVPの構成に応じて変更する可能性があります。

### 技術検証（PoC）

技術検証のコードと結果は、別のリポジトリ [ex-day/poc](https://github.com/ex-day/poc) にまとめています（例：pgvector を使った関連 Discovery の検索、#1）。検証を受けた判断は、このリポジトリの issue に記録します。

## Architecture Direction

現時点では以下の方向で検討しています。

- Monorepo構成
- WebとAPIの責務を分離可能な構造
- 検索エンジンから個別コンテンツへ直接流入できる構成
- PC / Mobileそれぞれに適したDiscovery体験
- Discovery Cardなど主要UIコンポーネントの共通化
- 将来的なAI・レコメンド機能の分離

技術構成は、MVP設計と実装を進めながら確定していきます。

将来的に独立性が高くなった機能については、別リポジトリへの分離も検討します。

例：

- Infrastructure / Cloud
- AI / Recommendation
- Data collection / Processing
- 外部連携サービス

## Documents

設計資料は [`docs/`](./docs/) 以下で公開しています。

現在、以下のドキュメントを順次作成・更新しています。

- Requirements
- MVP Scope
- Screen List
- Screen Transition
- Wireframe Specification

設計途中の内容も含まれるため、仕様は今後変更される可能性があります。

## License / Use of Ideas

このリポジトリは [MIT License](LICENSE) で公開しています。

コードや公開されたアイデアが、学習・派生プロジェクト・新しいサービスなどにつながることを歓迎します。ex-day の考え方を真似して、より大きなサービスが生まれるなら、それもうれしいことです。公開内容を参考にした場合は、可能な範囲で ex-day への言及や出典を残していただけるとうれしいです。

### このプロジェクトの将来について

ex-day は、次のどちらの形でも育っていく可能性があります。

- 参加者と一緒に、自分たちでサービスとして育てていく（商用での運営を含む）
- ex-day の運営やサービスを、ほかの個人・団体・企業に引き継いでもらい、育ててもらう（権利の譲渡や売却を含む）

参加・Contribution していただいた内容は、MIT License のもとで扱われ、上のどちらの形になっても引き続き使われます。Contribution に対する金銭的な対価は、現時点では約束していません。一方で、コミットや issue の記録には、誰が何をしたかが残ります。参加した人自身の実績やポートフォリオとして使ってください。

---

**ex-day is an open experiment in turning ideas into discoveries — and ideas into real projects.**
