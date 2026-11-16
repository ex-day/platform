# S03 Discovery のページ（discovery detail）

> 2026-11-16 根本整理（DEC-0019〜0023、[Issue #135](https://github.com/ex-day/platform/issues/135)）に合わせて書き直した。旧名「Discovery詳細」。v0.3 の定義（Discovery を会話の器とし、みんなの声（C26）を中に持つ形、成立状態 `UNESTABLISHED`／`ESTABLISHED`、タグ）は Git の履歴にある。本書で使う新しいコンポーネント（C28〜C41）・機能（F05〜F12）の番号は候補。

## 画面概要

1つの Discovery（どこで、何を、どう体験できるか）を見せ、次の人の体験に誘う（D。[DEC-0019](../../decisions/DEC-0019-what-ex-day-wants-most.md)）。今伝えたい価値を Hero で直感的に示し、関わる場所を役割・順番つきで地図に出し、どの会話から分かったのか（由来）をたどれるようにする。

会話は Discovery の中に持たない（会話が器。[DEC-0020](../../decisions/DEC-0020-place-conversation-discovery.md) 決定4）。このページは、由来の会話とわかってきたことを要約で見せ、話の続きは会話のページ（S14）と「この発見について話す」（入口＝この Discovery）で行う。

Discovery の多くは、会話から自動で成立する（[MVP スコープ](../../ex-day_mvp_scope.md) 7節）。ex-day が取り出したものであり、認定ではないことが分かるようにする。

## 画面パス

- `/discoveries/[id]`
- `[id]` は Discovery の、画面・URL に出す識別子。識別子の形式は物理設計で決める
- 今の実装（API のデータで表示。Issue #112）は v0.3 の形のまま。実装の段で移す。モック（`/mock/discoveries/[id]`）も v0.3 の形

## 対象端末

- PC
- モバイル

## 表示項目

データ元は論理エンティティ設計 v0.2 の Entity・属性で書く（API の項目名は API 設計で決める）。

| 論理名 | 物理名 | 種別 | 繰り返し | 親 | データ元 | データ項目 | 対象端末 | 備考 |
|---|---|---|---|---|---|---|---|---|
| 共通ヘッダー | → [C01](../components/C01-header.md) | component | - | - | - | - | PC/モバイル | |
| 場所の見出し | discovery_place_heading | link | 1..n | - | DiscoveryPlace・Place | 出す位置の場所の名称 | PC/モバイル | 「小机城址」「生麦 ほか」。押すとその場所の S13。出す位置は役割が体験できる場所・始まりの場所（論理設計 4.5節） |
| 表題 | discovery_title | text | - | - | Discovery | 表題 | PC/モバイル | |
| 観点 | discovery_aspects | text/link | 0..n | - | Subject（Discovery の対象・観点） | 代表表記 | PC/モバイル | 「歴史」「季節の楽しみ」「城郭」など。押すとその言葉で S02（対象のページは MVP の対象外） |
| Hero | → [C19](../components/C19-discovery-detail-hero.md) | component | - | - | Value・Condition・（Recommendation） | 今成立している価値と、その理由 | PC/モバイル | 今伝えたい価値を直感的に出す。Hero は常に出す（Issue #50） |
| 出所の表示 | discovery_origin_note | text | - | - | DiscoveryOrigin・Decision | 由来の種類、方式（自動／人） | PC/モバイル | 「みんなの会話から ex-day が取り出した発見です」「ex-day の発見（見本）」など。ex-day の認定ではないことを示す（下記の構成方針） |
| リアクション | → [C10](../components/C10-reaction-button.md) | component | - | - | Reaction | 種類ごとの数、自分の Reaction | PC/モバイル | Discovery への「わかる」「行ってみたい」 |
| 関わる場所 | discovery_places | section | - | - | - | - | PC/モバイル | Discovery は場所を1つ以上持つので、必ず出す |
| 関わる場所の地図 | → C41（場所の地図） | component | - | 関わる場所 | DiscoveryPlace・Place | 場所の位置・形、役割、順番 | PC/モバイル | 関わる場所をすべて地図に出す。順番のあるもの（コースの Discovery）は順番の番号と直線（道順ではない）（ドメイン設計 3.6.4節）。C41 を企画の項目と共用する（**案**） |
| 関わる場所の一覧 | → C28（場所カード） | component | 1..n | 関わる場所 | DiscoveryPlace・Place | 名称、役割、順番、期間 | PC/モバイル | 役割（記憶の場所、今の跡地、体験できる場所、食べられていた地域、始まり・経由・終わり等）を添える。押すとその S13 |
| ここから行ける | → C35（小さな地図） | component | 0..1 | - | Place | 出す位置の場所 | PC/モバイル | 出す位置の場所を起点に、ここから行ける範囲。押すと S12（`from`＝その場所）。計算できないときは出さない |
| その他の魅力 | → [C12](../components/C12-discovery-value.md) | component | 0..n | - | Value・Condition | 価値の説明、条件 | PC/モバイル | 今は成立していない価値（例：春の桜を、春以外に見たとき）。0件なら出さない |
| わかってきたこと | → [C25](../components/C25-discovery-emerging-terms.md) | component | 0..n | - | Finding | 種類、内容、裏付けの有無 | PC/モバイル | 由来の会話から読み取った説・価値のうち、この Discovery に関わるもの。0件なら出さない |
| 由来の会話 | → C40（会話カード・会話の一覧） | component | 0..n | - | DiscoveryOrigin・Conversation | 最初の Post の要約、返信の数 | PC/モバイル | この Discovery が取り出された会話と、入口＝この Discovery の会話。押すと S14 |
| この発見について話す | talk_here | button | - | - | - | - | PC/モバイル | C27（新しい話を始める）を、入口＝この Discovery で開く |
| 関わる企画 | → C29（企画カード） | component | 0..n | - | ProjectItem・Project | 表題、型、作った人、この企画の中の順番・順位 | PC/モバイル | この Discovery、またはこの Discovery の場所を項目に持つ企画。0件なら出さない |
| 派生元の Discovery | → [C03](../components/C03-discovery-card.md) | component | 0..n | - | DiscoveryRelation（派生） | 表題、要約 | PC/モバイル | 0件なら出さない |
| 派生した Discovery | → [C03](../components/C03-discovery-card.md) | component | 0..n | - | DiscoveryRelation（派生） | 表題、要約 | PC/モバイル | 「この発見から広がった発見」。0件なら出さない |
| 関連する Discovery | → [C03](../components/C03-discovery-card.md) | component | 0..n | - | DiscoveryRelation（関連）、Subject | 表題、要約 | PC/モバイル | 明示的な関連と、Subject を介した意味の関連。0件なら出さない |
| 統合先への案内 | → [C15](../components/C15-related-discovery.md) | component | 0..1 | - | DiscoveryRelation（統合） | 統合先の表題 | PC/モバイル | 統合された Discovery を開いたとき。統合先の S03 へ案内する |
| これは違う | → C39（通報） | component | - | - | - | - | PC/モバイル | 「この発見はおかしい・違う」を伝える。通報の理由の分類の1つとして扱う（**案**。軽いフィードバックにするかは要検討。MVP スコープ 7節） |
| 運営の操作 | → C38（運営の操作） | component | 0..1 | - | Decision | 判断の記録 | PC/モバイル | 運営としてログインしたときだけ。成立の取り消し、場所・価値の付け替え、統合（F12）、非公開（F10）と、判断の記録（自動で成立したか、規則・解析の版） |
| 共通フッター | → [C02](../components/C02-footer.md) | component | - | - | - | - | PC/モバイル | |

## アクション

- 初期表示
  - 指定された識別子の Discovery を表示する。成立した Discovery だけがこのページを持つ（成立前の状態はない。DEC-0020 決定9）
  - 非公開の Discovery は、一般のユーザーには表示しない（直接の URL でも返さない）。運営は表示でき、非公開であることを出す（画面一覧「運営の権限」）
  - 統合された Discovery は、統合先への案内を出す（自動では移らない。**案**。S13 と同じ）
  - Value の Condition と、今の Context（今の時期、現在地等）を評価した Recommendation を Hero に渡す。評価のしかた（今成立する Value は Hero、今成立しない Value は「その他の魅力」、評価に要る情報が足りないときは成立・不成立を推測しない）は v0.3 の考え方を引き継ぐ。評価の実装の置き場所は後続の設計で決める
  - Discovery の現在状態（「今シーズンの情報あり」「開催中らしい」）は、由来の会話と入口＝この Discovery の会話の直近の Post・Reaction から導き、Hero に添える（論理設計 4.5節の要検討：どの会話を材料にするか）
  - 関わる場所は、DiscoveryPlace をすべて、役割・順番つきで地図と一覧に出す
- 場所の見出し・関わる場所 を押下：その場所の S13 へ
- 観点 を押下：その言葉を条件に S02 へ
- ここから行ける を押下：S12 へ（`from`＝出す位置の場所）。戻るとこのページの表示位置を保つ
- この発見について話す を押下
  - C27 を、入口＝この Discovery で開く。会話の始まりの時点で、この Discovery の出す位置の場所を採用するので、場所の共有の誘いは出ない（論理設計 4.3節の案）
- 由来の会話 を押下：S14 へ
- 関わる企画 を押下：S15 へ
- 派生元・派生した・関連する Discovery、統合先への案内 を押下：その S03 へ
- わかってきたことにリアクション（C25）：価値には「わかる」「行ってみたい」、裏付けのない説には「そうかも」
- これは違う を押下（C39）：理由を選んで送る。F10 を呼ぶ。送った内容は S18 の一覧と、自動で成立した Discovery の見直し（F12）の材料になる
- 遷移元へ戻る：遷移元（S01・S02・S13・S15 等）の状態と表示位置を戻す

## 構成方針

- 上から「場所の見出し・表題・観点」「Hero」「出所の表示とリアクション」「関わる場所（地図と一覧）」「その他の魅力」「わかってきたこと・由来の会話・この発見について話す」「関わる企画」「関係する Discovery」の順に置く。**PC** は、関わる場所の地図・ここから行ける・関わる企画を横の列に置く。**モバイル** は1列
- **「どこで」を最初に見せる**：Discovery は「どこで」が付いて初めて Discovery なので（ドメイン設計 3.6.1節）、表題の上に場所の見出しを置き、関わる場所の地図をページの前半に置く（v0.3 の「場所は地図を埋め込まずテキストで表示」を改める）
- **出所を見せ、認定に見せない**：会話から自動で成立した Discovery は「みんなの会話から ex-day が取り出した発見です」と出し、由来の会話をすぐ下でたどれるようにする。運営が登録した見本は「ex-day の発見（見本）」と出す。わかってきたことの説を正解のように見せない
- 会話の続きはこのページでは書かない。由来の会話（S14）と、「この発見について話す」（新しい会話）へ案内する
- Hero・その他の魅力の見せ方（画像・映像がなくてもテキストで成り立つ、Recommendation が0件でも Hero を出す、その他の魅力は今成立しない Value、モバイルは横スクロール、PC はグリッド）は v0.3 を引き継ぐ（Issue #50・#52）
- 関係する Discovery（派生元・派生した・関連）の3つの枠と、モバイルの横スクロール・「さらに見る」で S02 へ、の方針は v0.3 のワイヤーフレームのレビュー結果を引き継ぐ。派生・統合は MVP では運営が手で決めるので、多くの Discovery ではこれらの枠は出ない
- 中身のない区画は出さない

## 判断待ち・検討事項

- 関わる場所の地図に C41 を共用する案（企画の項目の地図と同じ部品）。共用するなら C41 の名前を「場所の地図」のように広げる
- 「これは違う」を通報の理由の1つにするか、軽いフィードバックとして別に持つか
- 入口＝この Discovery の会話で、採用する場所（出す位置の場所。複数あるときにどれか）
- 統合された Discovery を開いたときに、自動で移すか、案内だけにするか（S13 と合わせる）
- Discovery の現在状態の材料にする会話の範囲（論理設計 4.5節）
- v0.3 から引き継ぐ検討事項：Hero の Recommendation の初期選択と表示順、Recommendation 0件の標準 Hero の文言（Issue #52）、評価不能のときの表示、関係する Discovery の見出しの分かりやすさ、横スクロールの上限件数とカードの幅
