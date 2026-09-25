# 画面一覧

IDの採番・廃止・参照のルールは[各種設計資料の作成方針](各種設計資料作成方針.md)の「画面・コンポーネント・機能のIDの採番ルール」（[DEC-0011](decisions/DEC-0011-id-numbering-rules.md)）に従う。IDは変えず、廃止したIDは再利用しない。

## 現行

|画面ID|画面名|画面パス|備考|
|----|----|----|----|
|S01|[Discovery提案（サービスTOP）](ui/screens/S01-top.md)|`/`||
|S02|[Discovery探索一覧](ui/screens/S02-discovery-list.md)|`/discoveries`||
|S03|[Discovery詳細](ui/screens/S03-discovery-detail.md)|`/discoveries/[id]`|`[id]`はモック用の仮ID(例: `sample-1`)。本番のDiscovery識別子の形式は別途決定する(ex-day_logical_entity_design.md参照)。wikiのようなコンテンツ名ベースのURLも検討したが、名称の決定者・一意性(同名Subjectが別Discoveryとして存在し得る)・表題変更時の旧URL扱い等の追加論点が生じるため、今回はID方式のまま進める|
|S05|[知識・疑問詳細](ui/screens/S05-contribution-detail.md)|`/contributions/[id]`|Discoveryの元となった知識|
|S06|[知識・疑問一覧](ui/screens/S06-contribution-list.md)|`/contributions`|Discoveryの元となった知識の一覧|
|S07|[新規ユーザー登録](ui/screens/S07-signup.md)|`/signup`||
|S08|[ユーザー情報更新](ui/screens/S08-user-edit.md)|`/account`||
|S09|[自分の投稿一覧](ui/screens/S09-user-contribution-list.md)|`/account/contributions`|Discovery未紐付けを含む、自分が登録した知識・疑問の一覧|
|S10|ex-dayについて|`/about`||

新規投稿（新しい話を始める）は画面ではなく、ヘッダーの「話す」から開くモーダル[C27（新しい話を始める）](ui/components/C27-post-new.md)で行う。URL（`/posts/new`）を持ち、直接開ける（Issue #67）。S05（知識・疑問詳細）・S06（知識・疑問一覧）・S09（自分の投稿一覧）の要否と再構成は[Issue #68](https://github.com/ex-day/platform/issues/68)の判断待ちのため、名前・内容を変更していない。

## 廃止済み

IDは欠番とし、再利用しない。定義ファイルは経緯の参照のために残している。

|画面ID|画面名|旧画面パス|廃止の理由|代わり|関係するDEC／Issue|
|----|----|----|----|----|----|
|S04|[知識・疑問登録／編集（廃止）](ui/screens/S04-contribution-save.md)|登録: `/contributions/new`<br>編集: `/contributions/[id]/edit`|新規投稿はモーダルで行う。投稿時に種類・場所・時期を入力させない|新規投稿：[C27（新しい話を始める）](ui/components/C27-post-new.md)。編集：S03の会話（[C26（みんなの声）](ui/components/C26-post-thread.md)）の中での編集|DEC-0005、DEC-0010、Issue #67|
|S11|[知識・疑問登録確認画面（廃止）](ui/screens/S11-contribution-save-confirm.md)|`/contributions/new/confirm`|投稿してから解析する流れになり、投稿前の確認の段階がなくなった|[F02（投稿後の会話の解析）](ui/functions/F02-conversation-analysis.md)、[F03（似たDiscoveryの案内）](ui/functions/F03-similar-discovery-guide.md)|DEC-0005、DEC-0009、DEC-0010、Issue #67|

## S01 / S02の責務境界

S01とS02の責務はPC／モバイルで共通とし、Discoveryを選ぶ主体とユーザーの探索意思によって区別する。境界は検索条件をユーザー自身が入力したかどうかではなく、ユーザーに明確な探索意思があるかどうかで判断する。

|観点|S01 Discovery提案（サービスTOP）|S02 Discovery探索一覧|
|----|----|----|
|役割|ex-dayによるDiscoveryの提案|ユーザーによるDiscoveryの探索|
|主体|ex-day|ユーザー|
|探索意思|必須ではない|明確な探索意思がある|
|Discoveryとの接触|受動的・偶発的|能動的・意図的|
|位置・時間・興味の扱い|推薦のための材料|探索・検索条件|
|目的|意識していなかったDiscoveryとの出会いを作る|意図したDiscoveryを探せるようにする|

S01の「もっと見る」で提案テーマをさらに見ようとした時点、S03の関係Discoveryの「さらに見る」で関係するDiscoveryをさらに見ようとした時点、共通ヘッダーのC06で検索を実行した時点で、ユーザーに明確な探索意思が生じたものとしてS02へ遷移する。S02の探索条件には、C06等でユーザーが指定した条件だけでなく、S01等の他画面から引き継いだ条件も含む。

S01は現在位置（取得できない場合は既定エリア）を推薦材料とする。C06で位置を指定した場合もS01内の位置だけを変更する導線とはせず、指定条件を引き継いでS02へ遷移する。S02を条件なしで直接開いた場合は、現在地（取得できない場合は既定エリア）と既定半径を既定条件として表示する。

モバイルのS01は、ex-dayによる複数の提案軸をタブで切り替え、各軸のDiscoveryを縦方向に継続して閲覧できる構成とする。この構成により、明確な探索意思が生じる前のDiscovery提案・偶発的な出会いというサービスTOPの責務をS01が担う。タブ内の最大件数、無限スクロール／仮想化の実現性、C05「もっと見る」との役割分担は未確定とし、Mockで確認する。
