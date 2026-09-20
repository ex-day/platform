# 画面一覧
|画面ID|画面名|画面パス|備考|
|----|----|----|----|
|S01|[TOP](ui/screens/S01-top.md)|`/`||
|S02|[Discovery一覧](ui/screens/S02-discovery-list.md)|`/discoveries`||
|S03|[Discovery詳細](ui/screens/S03-discovery-detail.md)|`/discoveries/[id]`|`[id]`はモック用の仮ID(例: `sample-1`)。本番のDiscovery識別子の形式は別途決定する(ex-day_logical_entity_design.md参照)。wikiのようなコンテンツ名ベースのURLも検討したが、名称の決定者・一意性(同名Subjectが別Discoveryとして存在し得る)・表題変更時の旧URL扱い等の追加論点が生じるため、今回はID方式のまま進める|
|S04|[知識・疑問登録／編集](ui/screens/S04-contribution-save.md)|登録: `/contributions/new`<br>編集: `/contributions/[id]/edit`|Discoveryの元となる知識を登録・編集する|
|S05|[知識・疑問詳細](ui/screens/S05-contribution-detail.md)|`/contributions/[id]`|Discoveryの元となった知識|
|S06|[知識・疑問一覧](ui/screens/S06-contribution-list.md)|`/contributions`|Discoveryの元となった知識の一覧|
|S07|[新規ユーザー登録](ui/screens/S07-signup.md)|`/signup`||
|S08|[ユーザー情報更新](ui/screens/S08-user-edit.md)|`/account`||
|S09|[自分の投稿一覧](ui/screens/S09-user-contribution-list.md)|`/account/contributions`|Discovery未紐付けを含む、自分が登録した知識・疑問の一覧|
|S10|ex-dayについて|`/about`||
|S11|[知識・疑問登録確認画面](ui/screens/S11-contribution-save-confirm.md)|`/contributions/new/confirm`|現時点ではMock・画面遷移確認用の独立パス。将来、S04と同一ページ内の確認状態に変更する可能性がある|
