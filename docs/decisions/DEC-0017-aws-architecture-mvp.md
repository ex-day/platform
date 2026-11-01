# DEC-0017: MVP は AWS の管理型のサービスで、本番だけを持ち、手元の Docker で試せる範囲が大きい構成にする

## Status

Accepted（方針は人間が 2026-11-01 に決定。本文は Claude が作成し、PR #131 の2回のレビュー（短命の検証環境、DB の容量より CPU・メモリ・同時の接続、テストの役割分担、費用のシナリオ、t4g.medium・CPU クレジット・内部の ALB）を経て 2026-11-01 に Accepted）

## Date

2026-11-01

## Context

#129 で、ホスティング・インフラの構成を決める。前提：

- DB は PostgreSQL 18＋PostGIS＋pgRouting（将来 pgvector）が必須。AWS の RDS for PostgreSQL で使えることを確かめた（#129 の本文）
- 認証は Cognito Essentials（DEC-0016）。ログインは標準の OIDC でつなぎ、トークンはサーバー側に持つ
- 画面は Next.js（App Router、SSR・OGP）、API は Spring Boot 4.1・Java 25 のコンテナ
- AI の解析（F02）は、毎回起動して終わる定期処理（#127）
- 運営は1人。部品を増やしすぎない（DEC-0014 の議論でも「仕組みを増やすのは面倒」）
- 費用の見積もり（`docs/infra/cost-model.md`）：Fargate 2タスク＋ALB＋RDS db.t4g.small で、月100万PV まで固定費 約2.1万円（低い負荷のときの床）

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

### 3. 常に動かす環境は本番だけ。開発・確かめは手元の Docker と、短命の検証環境

- 常に動かす検証の環境（ステージング）は持たない。規模が大きくなり、開発者が増えたら足す（人の判断）
- **短命の検証環境**：インフラを変えるとき・リリースの候補のときだけ、本番と同じ CDK で AWS に作り、スモークテストのあとに自動で消す（作ってからの時間で消す。消し忘れは AWS Budgets でも見張る）。DB はふだんはコンテナの PostgreSQL、DB を変えるときと定期にだけ一時の RDS を含める。1回 約35〜50円（2時間、`docs/infra/cost-model.md`）
  - 手元の Docker と CDK の点検（`cdk synth`）では確かめられないものを、ここで確かめる：CloudFront → ALB → ECS での Host・cookie・CSRF の受け渡しとキャッシュの設定（CloudFront の VPC オリジンで ALB を内側に置く構成、定額プランを含む）、実際の IAM の権限と秘密の注入、ECS の入れ替えと失敗したときの戻し、RDS への拡張の導入・マイグレーション・性能、Cognito の独自ドメインを含む本物の流れ
  - GitHub Actions から、短命の環境用に権限を絞った役割（OIDC）で作る・消す
- **手元の Docker で試せる範囲を大きくする**（人の判断）。そのために：
  - アプリの中身は、標準のやり取りで外とつなぐ（認証は OIDC、DB は PostgreSQL、ファイルは S3 の API、ログは標準出力、設定は環境変数）
  - AWS にしかない仕組み（Lambda のトリガー、Step Functions、SQS など）を、アプリの処理の流れに入れない。キューが要るときは、まず PostgreSQL の表で作る
  - 同じコンテナイメージを手元と本番で使い、違いは設定だけにする
  - CloudFront・ALB の振り分けは、手元ではリバースプロキシのコンテナで同じにする
  - 認証は、手元の OIDC のモック（ネットにつながずに動かす）と、開発用の Cognito のユーザープール（本物の流れを確かめる）を選べるようにする

### 4. テストは AI が書き・保ち・失敗を調べ、合否は CI の道具で決める

- **役割の分担**：AI はテストを書き、保ち、失敗したときに原因を調べる。合否は、再現できる CI の道具（JUnit、Testcontainers、Playwright、CDK の assertions、cdk-nag、負荷・セキュリティの検査）で決める
- ログイン以外の画面・API のパターンの網羅は、手元と CI（GitHub Actions）で回す。ログインした状態は OIDC のモックで作る
- 単体・API・認可・DB の拡張・経路の計算・マイグレーション・画面の通し（E2E）・アクセシビリティ・負荷・インフラの定義・セキュリティは、広く自動にする
- 本番には、デプロイのあとと定期に、Playwright で読むだけの見回り（スモークテスト）を行う。書き込みはせず、広告は読み込まない（AdSense の無効なトラフィックを避ける）
- 本物の Cognito のログインは、まず人が確かめる（人の判断）。短命の検証環境で、Playwright の仮想の認証器（WebAuthn）を使えば、パスキーの登録・ログイン・失敗の場合も自動にできる見込み（メールのコードの受け取り方を含め、作るときに確かめる）。そうなれば、人が確かめるのは Google の同意の画面、実際の端末の使い勝手、探索的な確かめに絞れる
- 詳しくは `docs/infra/aws-architecture.md` の「テストと確かめ方」

### 5. インフラは AWS CDK（TypeScript）で書く

- 手作業で作らない（再現できないため）
- Terraform にも関心はあるが、いったん CDK（人の判断）

### 6. NAT ゲートウェイを置かない

- Fargate はパブリックのサブネットに置き、セキュリティグループで ALB からだけ受ける。外への通信はパブリック IP で出す。RDS はプライベートのサブネット
- 理由：NAT は月 約$45 かかり、当初の固定費（月 約2.1万円）に対して大きい

### 7. DB は、容量ではなく CPU・メモリ・同時の接続で大きさを決める

- 固定費の月 約2.1万円（db.t4g.small）は、低い負荷のときの床であり、予算の値ではない。DB のメモリを増やした比較（db.t4g.medium、実測前の仮置き）で 月 約3.2万円、利用者が増えたあと（Multi-AZ、タスク4つ以上）で 月 約13万円（AWS だけ）を見込む（`docs/infra/cost-model.md` の「構成のシナリオ」）
- db.t4g.small と medium は、どちらも 2 vCPU・基準の CPU 20% で、medium はメモリが2倍になるだけ。**CPU が詰まる場合、medium に上げても解決しない**。測った結果で、t4g.large・xlarge、CPU が一定の M 系、メモリ優先の R 系を比べて選ぶ（https://aws.amazon.com/rds/instance-types/ ）
- pgRouting を使うと、ストレージより先に、経路の探索の CPU、グラフを扱うメモリ、API と AI の解析の DB の接続の取り合い、同時の経路の検索の p95・p99、t 系のインスタンスの CPU クレジットが詰まる見込み
- 代表の道路・施設のグラフで、経路の検索の p95・CPU・メモリ・同時の接続の数を測り、どの種類・大きさへ上げるかの条件を決めて、運用の資料に残す（公開前のゲート）
- T4g は Unlimited モードで、24時間の平均が基準の CPU を超えると、CPU クレジットの追加料金（RDS for PostgreSQL で $0.075／vCPU 時間、予約の割引の対象外）がかかる。費用のモデルに感度を入れた（平均 50% で 月 約5,800円）
- AI の解析などの定期タスクが画面・API を圧迫しないよう、タスクの CPU・メモリに加えて、DB の接続の数を絞る（接続のプールを小さくする、問い合わせの時間の上限など）。CloudWatch の `CPUCreditBalance`（残り）・`CPUSurplusCreditBalance`（前借り）・`CPUSurplusCreditsCharged`（追加で請求されたもの）を見て、アラームを置く。追加料金を、実際の費用と上げる判断に結び付ける

### 8. 本番のデプロイを安全にする

- ECS のデプロイの回路遮断（deployment circuit breaker）と CloudWatch のアラームで、失敗したら自動で戻す
- コンテナイメージは digest で固定して使う
- デプロイのあとにスモークテスト（決定4）
- DB のマイグレーションは、前の版のアプリとも動く形（後方互換）にする（足す → 移す → 消す の順）

### 公開前のゲート

公開（利用者に開く）前に、次を決めて確かめる。値は人が決める。

- 短命の検証環境で、決定3に挙げたものを確かめた
- DB の大きさを上げる条件（決定7の測定）
- Single-AZ で動かす期間と、Multi-AZ にする条件
- 復旧の目標（RPO：どこまで戻せればよいか、RTO：どれだけで戻すか）、RDS の自動バックアップの保持期間
- スナップショットから戻す練習を1回は行った
- デプロイの自動の戻し（決定8）が動くことを確かめた

## Reason

- 運営1人で、OS・サーバーのセキュリティを持たずに済む
- 部品が少なく、画面・API・定期処理が同じ仕組み（ECS）に載る
- 手元の Docker と本番の違いが小さく、常に動かす検証の環境がなくても、多くを手元で確かめられる。手元で確かめられない AWS の部分は、短命の検証環境で確かめ、固定費はほぼ増えない
- 費用の見積もりの構成（固定費の床 約2.1万円、DB のメモリを増やした比較で 約3.2万円）と合う

## Alternatives

- **EC2 を1台（ALB なし）**：固定費は月 約1.4万円まで下がるが、OS の更新・障害の対応を持つ
- **App Runner**：新規の受け付けを終えている
- **画面を Amplify Hosting・Vercel に置く**：入口とドメインが分かれ、仕組みが増える。cookie の扱いも複雑になる
- **ログインの処理を Next.js に置く（Auth.js）**：Spring はトークンを確かめるだけになるが、トークンの置き場を考え直すことになる
- **Aurora PostgreSQL**：PostgreSQL 18 と拡張への対応が未確認、費用が高い
- **AI の解析を Lambda で**：仕組みとコードの置き場が増え、手元で同じに動かしにくい
- **Terraform**：クラウドを問わない。今は CDK
- **常に動かす検証の環境を持つ**：固定費が約2倍。開発者が増えたら。それまでは短命の検証環境で代える

## Consequences

- `compose.yaml` に、リバースプロキシ、OIDC のモック、S3 互換のもの、pgRouting・pgvector を足す（実装の Issue で）
- API に、Spring Security の OAuth2 Client・Spring Session JDBC・Cognito 専用の3か所（DEC-0016）を入れる。手元のモックのときの差し替えも
- 画面の SSR から API を呼ぶとき、cookie を受け渡す
- AI の解析（#127）は、API と同じイメージの別の起動コマンドとして作る
- CDK のコード（置き場所は実装の Issue で決める）、GitHub Actions のデプロイ（OIDC で AWS に入る）
- CI に、`docker compose up` した構成への Playwright の E2E と、CDK の点検のジョブを足す。本番の見回り、短命の検証環境を作る・消すジョブも（実装の Issue で）
- 経路の計算の負荷の測定（決定7）を、実装の前か直後に行う（ex-day/poc の到達圏の PoC の続きとして）
- `docs/フロントエンド技術方針.md` の「後日決定：ホスティング／インフラ構成」は、この DEC で決まった

## Deferred / Open Issues

- `docs/infra/aws-architecture.md` の「作るときに確かめること」（ECS Express Mode、Spring Session JDBC と SSR の cookie、OIDC のモックと差し替え、拡張の版をそろえる方法、CDK のスタックの分け方と DB を消さない設定、短命の検証環境の作り方）
- 公開前のゲートの値（RPO・RTO、バックアップの保持期間、Single-AZ の期間、DB を上げる条件）
- ドメインの取得（`.jp` か、ほかか）と DNS（Route 53）
- 監視・アラームの中身（何を見て、どこへ知らせるか）
- 将来の到達圏の R5（大きめのメモリ。DEC-0014）を載せるとき：ECS に載せるか

## Revisit Conditions

- 開発者が増えたとき（検証の環境を足す）
- 利用者が増え、Fargate・RDS の大きさや Multi-AZ が要るとき（`docs/infra/cost-model.md`）
- 手元・短命の検証環境で確かめられないことによる本番の障害が続くとき（常に動かす検証の環境を早める）
- 経路の計算の測定で、DB を上げる条件に当たったとき（決定7）
- AWS の料金・サービスの提供が大きく変わったとき

## Related Issues / PRs

- #129（本 DEC）、#75・DEC-0016（認証）、#81（費用）、#127（F02 の解析）、DEC-0014（到達圏）
- PR #130（`docs/infra/`）、ex-day/poc#38（Cognito × Spring Security）
