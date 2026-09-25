# 共通部品一覧

IDの採番・廃止・参照のルールは[各種設計資料の作成方針](各種設計資料作成方針.md)の「画面・コンポーネント・機能のIDの採番ルール」（[DEC-0011](decisions/DEC-0011-id-numbering-rules.md)）に従う。IDは変えず、廃止したIDは再利用しない。

## 現行

| 機能ID | 機能名                                                                            | 備考                                                                                   |
|--------|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| C01    | [共通ヘッダー](ui/components/C01-header.md)                                       | 新規投稿の入口「話す」を持つ（Issue #67）                                              |
| C02    | [共通フッター](ui/components/C02-footer.md)                                       |                                                                                        |
| C03    | [Discovery Card](ui/components/C03-discovery-card.md)                             |                                                                                        |
| C04    | [Discovery List](ui/components/C04-discovery-list.md)                             | C03（Discovery Card）の集合（検索結果等）                                              |
| C05    | [Discovery Section](ui/components/C05-discovery-section.md)                       | C03（Discovery Card）の集合（嗜好等のグループ化結果）                                  |
| C06    | [Discovery検索・絞り込み](ui/components/C06-search.md)                            |                                                                                        |
| C07    | [ドロップダウンメニュー](ui/components/C07-dropdown-menu.md)                      | ユーザーメニュー。新規投稿の導線はC01（共通ヘッダー）の「話す」へ移した                |
| C08    | [ログイン認証ダイアログ](ui/components/C08-login-dialog.md)                       |                                                                                        |
| C09    | [ローディング](ui/components/C09-loading.md)                                      |                                                                                        |
| C10    | [リアクションボタン](ui/components/C10-reaction-button.md)                        | Post・Discovery・わかってきたこと（価値・説）・Valueへのリアクション（DEC-0009 決定9） |
| C11    | [Discovery Sections](ui/components/C11-discovery-sections.md)                     | モバイル時タブ管理と、PC時のSection管理を行う                                          |
| C12    | [Discovery Value](ui/components/C12-discovery-value.md)                           | Discoveryの魅力を表示する                                                              |
| C14    | [知識・疑問内容](ui/components/C14-contribution-content.md)                       | 投稿原文・添付資料・投稿情報を表示。要否と再構成は[Issue #68](https://github.com/ex-day/platform/issues/68)の判断待ち |
| C15    | [Discovery関連情報](ui/components/C15-related-discovery.md)                       | 確定した関連Discoveryを表示                                                            |
| C16    | [知識・疑問の現在状態](ui/components/C16-contribution-status.md)                  | 投稿者本人に現在の状態を表示。要否と再構成はIssue #68の判断待ち                        |
| C17    | [この投稿から見つかった価値](ui/components/C17-contribution-impact.md)              | Contributionからex-dayが解釈・形成した価値をまとめるセクションとして、投稿者本人にDiscovery単位のC20を0..n件表示。要否と再構成はIssue #68の判断待ち |
| C18    | [確認・補完導線](ui/components/C18-contribution-confirmation.md)                  | 確認が必要な投稿者本人に表示。要否と再構成はIssue #68の判断待ち                        |
| C19    | [Discovery詳細 Hero](ui/components/C19-discovery-detail-hero.md)                  | 評価済みRecommendationから今伝えたい価値を直感的に提示 |
| C20    | [Discovery Impact](ui/components/C20-discovery-impact.md)                         | 1つのDiscoveryについて、わかってきたこと・Valueの出所となった会話を示す単位。利用する画面（S05・S09）の再構成はIssue #68の判断待ち |
| C23    | [資料出典登録ダイアログ](ui/components/C23-media-source-dialog.md)                | Discoveryへ提供された資料ごとに出典状態と出典情報を登録・編集するダイアログ |
| C25    | [わかってきたこと（Discovery Emerging Terms）](ui/components/C25-discovery-emerging-terms.md) | 会話から読み取られたわかってきたこと（説・価値）を示し、種類ごとのリアクションを扱う（DEC-0009） |
| C26    | [みんなの声（Post Thread）](ui/components/C26-post-thread.md)                     | Discoveryの会話（Post）を時系列に表示し、その場で声を寄せられる。返信先の引用、タグ、派生の誘導を含む |
| C27    | [新しい話を始める（Post New）](ui/components/C27-post-new.md)                     | **番号は候補（レビューで確定）**。ヘッダーの「話す」から開く新規投稿のモーダル。URL（`/posts/new`）を持つ |

## 廃止済み

IDは欠番とし、再利用しない。定義ファイルは経緯の参照のために残している。

| 機能ID | 機能名 | 廃止の理由 | 代わり | 関係するDEC／Issue |
|--------|--------|------------|--------|--------------------|
| C13    | [Discovery Recommendation（廃止）](ui/components/C13-discovery-recommendation.md) | 表示責務がHeroと重複した | C19（Discovery詳細 Hero）へ責務統合 | S03 Heroレビュー（2026-09-19） |
| C21    | [Contribution CTA（廃止）](ui/components/C21-contribution-cta.md)                 | 新規投稿の入口はヘッダーの「話す」に置き換わった | C01（共通ヘッダー）の「話す」。S02の0件時の誘導は、S02の画面定義の中に「話す」のモーダルを開くボタンとして直接書く | Issue #67 |
| C22    | [場所入力（廃止）](ui/components/C22-location-input.md)                           | 投稿時に場所を入力させない | ―（場所はDiscoveryに帰属し、会話から形成する） | DEC-0005、Issue #67 |
| C24    | [時期・期間入力（廃止）](ui/components/C24-time-period-input.md)                  | 投稿時に時期を入力させない | ―（時期はDiscoveryに帰属し、会話から形成する） | DEC-0005、Issue #67 |
