# 機能一覧

画面・コンポーネントから呼び出される機能（F）の一覧。機能の定義は`docs/ui/functions/`に置く。IDの採番・廃止・参照のルールは[各種設計資料の作成方針](各種設計資料作成方針.md)の「画面・コンポーネント・機能のIDの採番ルール」（[DEC-0011](decisions/DEC-0011-id-numbering-rules.md)）に従う。IDは変えず、廃止したIDは再利用しない。

## 現行

| 機能ID | 機能名 | どこで使うか | 備考 |
|--------|--------|--------------|------|
| F01    | [Discovery Sections（discovery-sections）](ui/functions/F01-discovery-sections.md) | S01（Discovery提案）のC11（Discovery Sections） | 表示対象のC05（Discovery Section）を決定する |
| F02    | [投稿後の会話の解析（conversation-analysis）](ui/functions/F02-conversation-analysis.md) | 投稿のたびに裏で動く。結果はC25（わかってきたこと）、C26（みんなの声）の注記・派生の目印に表示 | **番号・分け方は候補（レビューで確定）**。旧S11（知識・疑問登録確認画面）の役割を置き換える。タグの表記の揺れの吸収（内部で持つタグ）を含む |
| F03    | [似たDiscoveryの案内（similar-discovery-guide）](ui/functions/F03-similar-discovery-guide.md) | C27（新しい話を始める）の投稿直後の表示 | **番号・分け方は候補（レビューで確定）**。DEC-0010 決定1 |
| F04    | [タグの補完（tag-suggest）](ui/functions/F04-tag-suggest.md) | C27（新しい話を始める）とC26（みんなの声）の入力欄 | **番号・分け方は候補（レビューで確定）**。「#」のあとの補完と、入力欄の中の#タグの強調（投稿後の表示と同じ見た目）。AIを使わない。文中の言葉からのチップは廃止（Issue #67） |

## 廃止済み

現時点でなし。
