# 共通部品一覧
| 機能ID | 機能名                                                                            | 備考                                                                                   |
|--------|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| C01    | [共通ヘッダー](ui/components/C01-header.md)                                       |                                                                                        |
| C02    | [共通フッター](ui/components/C02-footer.md)                                       |                                                                                        |
| C03    | [Discovery Card](ui/components/C03-discovery-card.md)                             |                                                                                        |
| C04    | [Discovery List](ui/components/C04-discovery-list.md)                             | C03 Discovery Cardの集合（検索結果等）                                                 |
| C05    | [Discovery Section](ui/components/C05-discovery-section.md)                       | C03 Discovery Cardの集合（嗜好等のグループ化結果）                                     |
| C06    | [Discovery検索・絞り込み](ui/components/C06-search.md)                            |                                                                                        |
| C07    | [ドロップダウンメニュー](ui/components/C07-dropdown-menu.md)                      |                                                                                        |
| C08    | [ログイン認証ダイアログ](ui/components/C08-login-dialog.md)                       |                                                                                        |
| C09    | [ローディング](ui/components/C09-loading.md)                                      |                                                                                        |
| C10    | [リアクションボタン](ui/components/C10-reaction-button.md)                        |                                                                                        |
| C11    | [Discovery Sections](ui/components/C11-discovery-sections.md)                     | モバイル時タブ管理と、PC時のSection管理を行う                                          |
| C12    | [Discovery Value](ui/components/C12-discovery-value.md)                           | Discoveryの魅力を表示する                                                              |
| C13    | [Discovery Recommendation（廃止）](ui/components/C13-discovery-recommendation.md) | Heroへ責務統合。ID保持・再利用禁止                                                     |
| C14    | [知識・疑問内容](ui/components/C14-contribution-content.md)                       | 投稿原文・添付資料・投稿情報を表示                                                     |
| C15    | [Discovery関連情報](ui/components/C15-related-discovery.md)                       | 確定した関連Discoveryを表示                                                            |
| C16    | [知識・疑問の現在状態](ui/components/C16-contribution-status.md)                  | 投稿者本人に現在の状態を表示                                                           |
| C17    | [この投稿から見つかった価値](ui/components/C17-contribution-impact.md)              | Contributionからex-dayが解釈・形成した価値をまとめるセクションとして、投稿者本人にDiscovery単位のC20を0..n件表示 |
| C18    | [確認・補完導線](ui/components/C18-contribution-confirmation.md)                  | 確認が必要な投稿者本人に表示                                                           |
| C19    | [Discovery詳細 Hero](ui/components/C19-discovery-detail-hero.md)                  | 評価済みRecommendationから今伝えたい価値を直感的に提示 |
| C20    | [Discovery Impact](ui/components/C20-discovery-impact.md)                         | 一つのContribution × 一つのDiscoveryのImpactを1単位とし、対象Discoveryを識別情報／見出しとしてValue・利用結果・到達人数とともに表示 |
| C21    | [Contribution CTA](ui/components/C21-contribution-cta.md)                         | Discoveryの探索結果等を契機として知識・疑問などのContribution投稿を促し、S04へ案内 |
| C22    | [場所入力](ui/components/C22-location-input.md)                                   | 地点またはエリアを、現在地・地図・住所や施設名等の検索から入力するダイアログ |
| C23    | [資料出典登録ダイアログ](ui/components/C23-media-source-dialog.md)                | Contributionへ追加した資料ごとに出典状態と出典情報を登録・編集するダイアログ |
| C24    | [時期・期間入力（Time Period Input）](ui/components/C24-time-period-input.md) | 時期・日付・期間・時間From-Toを入力し、解釈前の情報を保持する共通コンポーネント。初期利用はDialog内を想定 |
| C25    | [わかってきたこと（Discovery Emerging Terms）](ui/components/C25-discovery-emerging-terms.md) | 議論中にContributionから継続的に抽出された未確定語句を提示。DEC-0005の継続的な価値抽出に対応 |
| C26    | [コメント（Contribution Thread）](ui/components/C26-contribution-thread.md)       | Discoveryに紐づくContributionを時系列に表示し、その場でコメント投稿できる。DEC-0005によりS03上で完結 |
