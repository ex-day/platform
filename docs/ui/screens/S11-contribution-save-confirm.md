# S11 知識・疑問登録確認画面（廃止）

> **廃止（2026-09-25）**。IDは欠番とし、再利用しない。本ファイルは過去の参照のために残す。以下の「廃止前の定義」は当時の記録であり、現在の仕様ではない。

## 廃止の理由と代わり
- 投稿してから解析する流れになり、投稿前に解析結果を確認する段階がなくなったため廃止する（[DEC-0005](../../decisions/DEC-0005-contribution-thread-and-comment.md) 決定7、[DEC-0010](../../decisions/DEC-0010-duplicate-conversations-and-posting-auth.md) 決定1）。
- 代わり：解析は投稿後に裏で動く[F02（投稿後の会話の解析）](../functions/F02-conversation-analysis.md)が行い、結果はS03（Discovery詳細）のC25（わかってきたこと）等に示す。投稿直後の似たDiscoveryの案内は[F03（似たDiscoveryの案内）](../functions/F03-similar-discovery-guide.md)と[C27（新しい話を始める）](../components/C27-post-new.md)が担う。
- 関係するDEC／Issue：DEC-0003、DEC-0005、[DEC-0009](../../decisions/DEC-0009-conversation-understanding-and-tags.md)、DEC-0010、[DEC-0011](../../decisions/DEC-0011-id-numbering-rules.md)、[Issue #67](https://github.com/ex-day/platform/issues/67)

## 廃止前の定義

（元の見出し：S11 contribution save confirm）

### 画面概要
S04で入力した知識・疑問の本文と添付資料、場所・時間・季節を確認し、解析された内容分類や関連Discoveryの候補を必要に応じて選択・修正して投稿する。候補を確定できない項目は未確定のまま投稿できる。

[S04](./S04-contribution-save.md)の時間入力2グループと[C24](../components/C24-time-period-input.md)の詳細情報は、[DEC-0002](../../decisions/DEC-0002-contribution-temporal-information.md)に従い、選択内容・入力原文を欠落なく引き継ぐ。以下の確認項目の詳細整合は[Issue #40](https://github.com/ex-day/platform/issues/40)で扱い、S11のMockおよびDiscovery／Valueへの具体的な変換規則は本変更では確定しない。

### 対象端末
- PC
- モバイル

### 表示項目
| 論理名                   | 物理名                                       | 種別      | 繰り返し | 親               | データ元 | データ項目         | 対象端末    | 備考                                                         |
|--------------------------|----------------------------------------------|-----------|----------|------------------|----------|--------------------|-------------|--------------------------------------------------------------|
| 共通ヘッダー             | → [C01](../components/C01-header.md)         | component | -        | -                | -        | -                  | PC/モバイル |                                                              |
| 投稿種別                 | contribution_type                            | text      | -        | -                | API      | type               | PC/モバイル | 知識／疑問                                                   |
| 本文                     | contribution_body                            | text      | -        | -                | API      | body               | PC/モバイル | 修正する場合はS04へ戻る                                      |
| 資料                     | contribution_media                           | file      | 0..n     | -                | API      | media              | PC/モバイル | 画像・動画・PDF等。添付されている場合に種別に応じて表示       |
| 場所                     | contribution_place                           | text      | -        | -                | API      | place              | PC/モバイル | 選択・入力された対象場所。未確定の場合はその旨を表示         |
| 場所の候補               | place_candidate                              | text      | 0..n     | -                | API      | place_candidates   | PC/モバイル | 候補が複数ある場合は個別に表示                               |
| 場所の手掛かり           | place_evidence                               | text      | 0..n     | 場所の候補       | API      | evidence           | PC/モバイル | 本文、添付資料、端末位置など。端末位置は対象場所と断定しない |
| 場所を選ぶ               | -                                            | button    | -        | 場所の候補       | -        | -                  | PC/モバイル | 候補を対象場所として選択                                     |
| 場所を修正する           | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 地名・地点・範囲を入力または修正                             |
| 場所を未確定にする       | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 候補がない、または判断できない場合                           |
| 時間・時期               | contribution_time                            | text      | -        | -                | API      | time               | PC/モバイル | 出来事の対象時点・期間。未確定の場合はその旨を表示           |
| 時間・時期の候補         | time_candidate                               | text      | 0..n     | -                | API      | time_candidates    | PC/モバイル | 本文や添付資料から得られた候補                               |
| 時間・時期を選ぶ         | -                                            | button    | -        | 時間・時期の候補 | -        | -                  | PC/モバイル | 候補が複数ある場合                                           |
| 時間・時期を修正する     | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 時点・期間を入力または修正                                   |
| 時間・時期を未確定にする | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 判断できない場合                                             |
| 季節・繰り返し時期       | contribution_season                          | text      | -        | -                | API      | season             | PC/モバイル | 該当する場合のみ表示。未確定も可                             |
| 内容分類                 | contribution_subject                         | text      | -        | -                | API      | subject            | PC/モバイル | 投稿が扱う話題。未確定の場合はその旨を表示                   |
| 内容分類の候補           | subject_candidate                            | text      | 0..n     | -                | API      | subject_candidates | PC/モバイル | 候補がある場合に表示                                         |
| 内容分類を選ぶ           | -                                            | button    | -        | 内容分類の候補   | -        | -                  | PC/モバイル | 候補を選択                                                   |
| 内容分類を修正する       | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 分類を入力または修正                                         |
| 内容分類を未確定にする   | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 判断できない場合                                             |
| 関連しそうなDiscovery    | → [C03](../components/C03-discovery-card.md) | component | 0..n     | -                | -        | -                  | PC/モバイル | 候補として表示。カード押下時のS03遷移はC03に従う             |
| Discoveryを関連付ける    | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 候補から選択。選択前に自動確定しない                         |
| 関連付けずに進む         | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 候補がない、または判断できない場合                           |
| 入力内容を修正する       | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 入力状態を保持してS04へ戻る                                  |
| 投稿する                 | -                                            | button    | -        | -                | -        | -                  | PC/モバイル | 確認した内容で投稿                                           |
| 共通フッター             | → [C02](../components/C02-footer.md)         | component | -        | -                | -        | -                  | PC/モバイル |                                                              |

### アクション
- 初期表示時
  - [S04 知識・疑問登録／編集](./S04-contribution-save.md)から入力内容と新規作成／編集の状態を引き継ぎ、本文・添付資料・入力済みの場所や時間を表示する
  - 本文、添付資料の手掛かり、利用できる端末位置などから、対象となる場所・時間／時期・季節・内容分類と関連Discoveryの候補を表示する
  - 候補が十分明確な項目は簡単に確認できる形で表示し、曖昧または根拠が食い違う項目は候補と手掛かりを示して選択・修正を促す
  - 推定できない項目は必要に応じて入力を促し、答えられない場合は未確定を選べるようにする
  - 添付資料の撮影場所・撮影日時や端末の現在地・投稿時刻を、投稿が扱う対象場所・対象時点として自動確定しない
- 場所・時間／時期・内容分類の候補選択／修正／未確定を選択した時
  - ユーザーの選択を確認内容に反映する。場所を修正した場合は、必要に応じて関連Discoveryの候補を更新する
- Discoveryを関連付ける／関連付けずに進むを選択した時
  - 選択されたDiscoveryとの関連付け、または関連先未指定を確認内容に反映する
- 入力内容を修正するを押下
  - 確認中の選択を保持してS04へ戻る。再度確認画面に進んだ際は、修正内容に基づく候補を表示する
- 投稿するを押下
  - 入力内容を検証し、禁止ワード・不適切資料などでNGの場合はエラーを表示する。候補の情報不足のみを理由に投稿を拒否しない
  - 認証済みの場合は、確認・修正された項目と未確定の項目、選択された関連Discovery（未指定可）を知識・疑問とともに保存する
  - 未認証の場合の投稿確定処理は、S04／認証フローの検討結果に従う
  - 保存成功後は[S05 知識・疑問詳細](./S05-contribution-detail.md)へ遷移する

### 検討事項
- 投稿確定時に認証を必須とするか、および認証が必要な場合に入力内容を認証後へ引き継ぐ方法は、S04／認証フローと合わせて検討する（Non-blocking）
- 資料の確認・表示方法、候補の表示件数、場所の入力方法は画面設計時に検討する。S11のMockはドメイン／EntityやIssue #1等の前提が確定するまで保留する方針を維持し、本変更では未確定事項を解決しない
- MVPで許可するMedia Type、ファイルサイズ、動画時間、Live Photos等の扱い、不適切資料の検出・モデレーション方式は[Issue #35](https://github.com/ex-day/platform/issues/35)で確定する
