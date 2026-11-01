# DEC-0017: MVP は AWS の管理型のサービスで、本番だけを持ち、手元の Docker で試せる範囲が大きい構成にする

## Status

Proposed（方針は人間が 2026-11-01 に決定。本文は Claude が作成し、レビュー待ち）

## Date

2026-11-01

## Context

#129 で、ホスティング・インフラの構成を決める。前提：

- DB は PostgreSQL 18＋PostGIS＋pgRouting（将来 pgvector）が必須。AWS の RDS for PostgreSQL で使えることを確かめた（#129 の本文）
- 認証は Cognito Essentials（DEC-0016）。ログインは標準の OIDC でつなぎ、トークンはサーバー側に持つ
- 画面は Next.js（App Router、SSR・OGP）、API は Spring Boot 4.1・Java 25 のコンテナ
- AI の解析（F02）は、毎回起動して終わる定期処理（#127）
- 運営は1人。部品を増やしすぎない（DEC-0014 の議論でも「仕組みを増やすのは面倒」）
- 費用の見積もり（`docs/infra/cost-model.md`）：Fargate 2タスク＋ALB＋RDS db.t4g.small で、月100万PV まで固定費 約2.3万円

## Decision

部品ごとの選び方と、手元の Docker との対応は `docs/infra/aws-architecture.md` にまとめる。

### 1. AWS の東京リージョンで、管理型のサービスを使う

- サーバー（OS）を自分で持たない。OS の更新やサーバーのセキュリティの問題を抱えない（人の判断）
- 画面（Next.js）と API（Spring Boot）は ECS Fargate、DB は RDS for PostgreSQL 18、入口は CloudFront → ALB、写真は S3、AI の解析は EventBridge Scheduler から ECS のタスクを起動する
- 最小の構成は Fargate＋ALB とする（EC2 を1台にして ALB をなくす形は採らない）

### 2. ログインの処理（BFF）は API（Spring）に置く

- Spring Security の OAuth2 Client でログインし、トークンは Spring のセッションに持つ。セッションは DB に置く（Spring Session JDBC）
- 画面の SSR は、ブラウザの cookie を付けて API を呼ぶ
- CloudFront でパスにより振り分け（`/api/*` は API、それ以外は画面）、画面と API を同じドメインにまとめる
- 理由：SSR があり、トークンなどの認証の情報をどこに置くかを、また考え直さずに済む（人の判断）。PoC（ex-day/poc#38）で確かめた形のまま

### 3. 環境は本番だけ。開発・確かめは手元の Docker

- 検証の環境は持たない。規模が大きくなり、開発者が増えたら足す（人の判断）
- **手元の Docker で試せる範囲を大きくする**（人の判断）。そのために：
  - アプリの中身は、標準のやり取りで外とつなぐ（認証は OIDC、DB は PostgreSQL、ファイルは S3 の API、ログは標準出力、設定は環境変数）
  - AWS にしかない仕組み（Lambda のトリガー、Step Functions、SQS など）を、アプリの処理の流れに入れない。キューが要るときは、まず PostgreSQL の表で作る
  - 同じコンテナイメージを手元と本番で使い、違いは設定だけにする
  - CloudFront・ALB の振り分けは、手元ではリバースプロキシのコンテナで同じにする
  - 認証は、手元の OIDC のモック（ネットにつながずに動かす）と、開発用の Cognito のユーザープール（本物の流れを確かめる）を選べるようにする

### 4. インフラは AWS CDK（TypeScript）で書く

- 手作業で作らない（再現できないため）
- Terraform にも関心はあるが、いったん CDK（人の判断）

### 5. NAT ゲートウェイを置かない

- Fargate はパブリックのサブネットに置き、セキュリティグループで ALB からだけ受ける。外への通信はパブリック IP で出す。RDS はプライベートのサブネット
- 理由：NAT は月 約$45 かかり、当初の固定費（月 約2.3万円）に対して大きい

## Reason

- 運営1人で、OS・サーバーのセキュリティを持たずに済む
- 部品が少なく、画面・API・定期処理が同じ仕組み（ECS）に載る
- 手元の Docker と本番の違いが小さく、検証の環境がなくても、多くを手元で確かめられる
- 費用の見積もりの構成（固定費 約2.3万円）と合う

## Alternatives

- **EC2 を1台（ALB なし）**：固定費は月 約1.5万円まで下がるが、OS の更新・障害の対応を持つ
- **App Runner**：新規の受け付けを終えている
- **画面を Amplify Hosting・Vercel に置く**：入口とドメインが分かれ、仕組みが増える。cookie の扱いも複雑になる
- **ログインの処理を Next.js に置く（Auth.js）**：Spring はトークンを確かめるだけになるが、トークンの置き場を考え直すことになる
- **Aurora PostgreSQL**：PostgreSQL 18 と拡張への対応が未確認、費用が高い
- **AI の解析を Lambda で**：仕組みとコードの置き場が増え、手元で同じに動かしにくい
- **Terraform**：クラウドを問わない。今は CDK
- **検証の環境を持つ**：固定費が約2倍。開発者が増えたら

## Consequences

- `compose.yaml` に、リバースプロキシ、OIDC のモック、S3 互換のもの、pgRouting・pgvector を足す（実装の Issue で）
- API に、Spring Security の OAuth2 Client・Spring Session JDBC・Cognito 専用の3か所（DEC-0016）を入れる。手元のモックのときの差し替えも
- 画面の SSR から API を呼ぶとき、cookie を受け渡す
- AI の解析（#127）は、API と同じイメージの別の起動コマンドとして作る
- CDK のコード（置き場所は実装の Issue で決める）、GitHub Actions のデプロイ（OIDC で AWS に入る）
- `docs/フロントエンド技術方針.md` の「後日決定：ホスティング／インフラ構成」は、この DEC で決まった

## Deferred / Open Issues

- `docs/infra/aws-architecture.md` の「作るときに確かめること」（CloudFront の VPC オリジン、ECS Express Mode、Spring Session JDBC と SSR の cookie、OIDC のモックと差し替え、拡張の版をそろえる方法、CDK のスタックの分け方と DB を消さない設定）
- ドメインの取得（`.jp` か、ほかか）と DNS（Route 53）
- バックアップ・復旧の目標（RDS の自動バックアップの保持期間、スナップショット）
- 監視・アラームの中身（何を見て、どこへ知らせるか）
- 将来の到達圏の R5（大きめのメモリ。DEC-0014）を載せるとき：ECS に載せるか

## Revisit Conditions

- 開発者が増えたとき（検証の環境を足す）
- 利用者が増え、Fargate・RDS の大きさや Multi-AZ が要るとき（`docs/infra/cost-model.md`）
- 手元で確かめられないことによる本番の障害が続くとき（検証の環境を早める）
- AWS の料金・サービスの提供が大きく変わったとき

## Related Issues / PRs

- #129（本 DEC）、#75・DEC-0016（認証）、#81（費用）、#127（F02 の解析）、DEC-0014（到達圏）
- PR #130（`docs/infra/`）、ex-day/poc#38（Cognito × Spring Security）
