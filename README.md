# ex-day platform

**ex-day** は、「予定された一日に、もう一つの発見を」をコンセプトとした行動・発見支援プラットフォームです。

現在地、時間、空き時間などのコンテキストをもとに、普段なら気づかなかった場所・出来事・地域の知識・疑問などとの偶発的な出会いを提供することを目指します。

## Repository

このリポジトリは、ex-dayの中核となるWebプラットフォームを管理するmonorepoです。

現時点では以下を対象とします。

- Webフロントエンド
- API
- 要求・設計ドキュメント

将来的に独立性が高くなった機能については、別リポジトリへ分離する可能性があります。

例：

- Infrastructure / Cloud
- AI / Recommendation
- Data collection / Processing
- 外部連携サービス

## Current Phase

現在はMVPの設計・プロトタイプ作成フェーズです。

主に以下を進めています。

- 要求整理
- MVPスコープ定義
- 画面一覧・画面遷移設計
- UIコンポーネント定義
- ワイヤーフレーム作成
- 技術構成検討

## Repository Structure

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

ex-dayの要求、MVPスコープ、UI/UX設計などを管理します。

### `apps/web`

Webフロントエンドを配置します。

現時点では **Next.js / React** を候補としています。

### `apps/api`

ex-dayのAPIを配置します。

現時点では **Spring Boot** を第一候補としていますが、MVPの構成に応じて変更する可能性があります。

## Architecture Policy

現時点では以下の方針で検討しています。

- Monorepo構成
- WebとAPIの責務を分離可能な構造
- 検索エンジンから個別コンテンツへ直接流入できる構成
- PC / Mobileで異なるDiscovery体験を許容
- Discovery Cardなど主要UIコンポーネントは共通化
- 将来的なAI・レコメンド機能の分離を考慮

技術構成はMVP設計の進行に合わせて確定します。

## Documents

設計資料は [`docs/`](./docs/) 以下を参照してください。

主なドキュメント：

- Requirements
- MVP Scope
- Screen List
- Screen Transition
- Wireframe Specification

## Status

🚧 **Planning / MVP Design**

現在、MVP実装に向けた画面・ユーザーフロー・ワイヤーフレームを設計中です。

---

ex-day is currently an experimental project under active design and development.