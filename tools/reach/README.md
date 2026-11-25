# tools/reach：寄り道の到達圏の版を作る

寄り道の到達圏（[F06](../../docs/ui/functions/F06-detour-reach.md)）の計算に使うデータ（版）を、N02（鉄道）と OSM（歩き）から作り、API が使う版を切り替える（#168。手順の決まり・性能・運用は #163）。2026-11-25 の人の判断で、取り込みは Python のコマンドにした（PoC #46 の pyosmium・psycopg の作りを引き継ぐ）。組み立ては SQL（pgRouting）で行う。

## 作るもの

| 段 | 入力 | 作るもの（DB） |
|---|---|---|
| 鉄道 | N02 の `N02-*_Station.geojson`・`N02-*_RailroadSection.geojson`（UTF-8） | 路線 `reach.line`（ないものだけ。表定速度は人が直す）、駅の場所（`place`・種類が駅）、計算の中の駅 `reach.station`・`reach.station_source`、版のグラフ `reach.node`・`reach.edge`・`reach.station_node` |
| 歩き | highway の道を切り出した `.osm.pbf` | 版の駅から場所への歩き `reach.place_walk`（片道15分まで）、版の歩きのグラフ `reach.walk_graph`・`reach.walk_node`・`reach.walk_edge`・`reach.walk_station`（DB V5） |

- **駅の名寄せ**：N02 の駅は、同じ路線・同じ駅名で 1km 以内のすでにある駅に結ぶ。駅のまとまり（N02 のグループ）は、結べた駅の場所か、同じ名前（「駅」を除く）で 500m 以内の種類が駅の場所に結び、なければ新しく作る（`source_dataset = 'N02'`）。年度が変わって駅コードが変わっても、駅と駅の場所は変えない。新設・廃止・名前の変更の運用は #163
- **歩き**：OSM の歩ける道（`highway` のうち motorway 系・建設中などを除き、`foot=no`・`use_sidepath` と `foot=yes` 等の無い `access=private/no` も除く）を交差点で区切ってグラフにし、つながっている点が100未満の島を除く（PoC #46。閾値は暫定）。駅のホームの重心・場所の到達点（徒歩）・場所の代表点を、300m 以内の道の点に結び、駅ごとに片道15分までたどる。道に結べない到達点は外す（DEC-0014 決定1）
- **徒歩の行き方がある組**は、行き方と比べるため、上限の15分を超えても道のりを持つ（上限なしの最短の道のり。まず駅と着く先のまわり約5km の道で探し、見つからなければ道の全部で探す）。道に結べない・道がつながらない組だけ行が無い。版を作るときに読んだ行き方は `reach.build_access` に記録し、API はそれだけを使う（あとで登録した行き方は、場所ごとの作り直しで足す）
- **寄り道先にしない場所**：都道府県・市区町村・町丁・字・駅
- **OSM の歩きのグラフは版ごとに DB に残す**（DB V5。2026-11-26 の人の判断）：登録・変更した場所の分だけ、API の処理（`PlaceWalkRefresher`）が `reach.refresh_place_walk` で歩きを作り直すため（F06「データの形」8）。辺の形は両端を結んだ線だけ残す（大きさを抑える）。ODbL の扱いは DEC-0014 決定1 のまま
- **場所の変更と重なっても落とさない**：版の行を足すトランザクションで鍵（`reach.build_lock_key()`）を排他で取る。版の行より前に確定した場所の変更はこの処理が読み、後の変更はトリガーが待ち行列（`reach.place_walk_job`）に積み、版が READY になってから API の処理が作り直す
- 計算の決まりの値（乗換10分・歩き15分・80m/分）は `reach_build.py` の先頭にあり、API の `exday.detour.*` と同じ値にする

## 使い方

```sh
pip install -r tools/reach/requirements.txt
export EXDAY_DSN=postgresql://exday:exday@127.0.0.1:5433/exday   # 既定は compose.yaml の DB

# OSM：Geofabrik から取ってきて、使う範囲の highway の道を切り出す（ex-day/poc reach/osmwalk/extract_highway.py）
python extract_highway.py ~/Downloads/kanto-261119.osm.pbf ~/Downloads/kanto-261119-5339-highway.osm.pbf

# 版を作る（BUILDING → READY。失敗したら FAILED で、使っている版はそのまま。鉄道のグラフが空なら失敗）
python tools/reach/reach_build.py build --version 2026-11-19.1 \
  --n02 ~/Downloads/N02-25_GML/UTF-8 --n02-year 2025 \
  --osm ~/Downloads/kanto-261119-5339-highway.osm.pbf --bbox 139 35.3333 140 36

# 使う版を切り替える（それまでの ACTIVE は RETIRED。切り戻しは前の版を activate する）
python tools/reach/reach_build.py activate --version 2026-11-19.1
python tools/reach/reach_build.py list
```

- `--bbox` は N02 の駅・区間を絞る範囲（西 南 東 北）。OSM の切り出しと同じ範囲にする。省くと N02 の全部
- 版を切り替えると、読んでいる途中の続きのページのカーソルは 400 になり、画面は最初から読み直す（F06「ページとカーソル」）
- 古い版を消すと、その版のグラフ・駅の点・歩きも消える（`DELETE FROM reach.build WHERE version = ...`）。ACTIVE の版は消さない

## 確かめたこと（2026-11-25）

クラウドの作業環境（2 CPU・7GB、PostgreSQL 16＋PostGIS 3.4＋pgRouting 3.6）で、サンプルデータを入れた DB に、N02-25（メッシュ 5339 の箱）と OSM の関東（kanto-261119、メッシュ 5339 の箱で切り出したもの）から版を作り、activate して API の SQL で到達圏を出せることを確かめた（結果は PR #165 のコメント）。

- 駅 1,496行・区間 3,194本 → グラフの点 3,266・辺 5,535、駅の場所 1,163。全体で1分55秒（OSM の読み込み 63秒、歩き 50秒）
- 到達圏の SQL（駅の時間・寄れる場所・帯）は、横浜駅・480分で合わせて約0.7秒（場所はサンプルの9件だけなので、寄れる場所の時間は目安にならない）

DB V5（歩きのグラフを残す形）で作り直したとき（2026-11-26、同じ環境・同じデータ）：サンプルに試しの場所 2,000件（東京の都心部に散らした点、うち200件に到達点）を足して、全体で2分43秒。残したグラフは点193万・辺266万で、`reach.walk_node` 301MB・`reach.walk_edge` 499MB。版を作ったときの歩き（一括）と、試しの場所を1件ずつ `reach.refresh_place_walk` で作り直した結果は、行（7,941行・場所 1,895件）も分（差 1e-14 以下）も一致した。作り直しは1件あたり約53ミリ秒（2,000件で1分46秒）。

## 更新と切り戻し（2026-11-26 の人の判断。F06「データの更新と切り戻し」）

- **N02・OSM**：半年に1回、新しいデータが出ているか確かめ、あれば `build` で版を作り、確かめて `activate` する。差分は確かめない
- **ex-day の場所・到達点・行き方**：版を待たずに、API の処理が場所ごとに作り直す（上）
- **切り戻し**：`build` のエラーは版が FAILED になり、使っている版はそのまま。切り替えたあとに監視でエラーの頻発が見つかったら、人が確かめてから前の版を `activate` する

## まだ決めていないこと（#163）

- 版を作り直す手順の詳細（駅の新設・廃止・名寄せの確かめ方）、古い版をいつ消すか
- 応答時間・同時実行の上限、作る時間の上限（全部の機能を作り終えてからの全体の性能テストで測る）
- 小さな島の閾値の確定（消す島に結び付く駅・場所・到達点の数と例を見て決める）
- 区間ごとの表定速度の上書き（`reach.speed_override`）を版を作るときに当てる（表はある。取り込みでの適用は後続）
- 事業者・路線の呼び名の表（`reach.operator_display`・`reach.line_display`。DB V5）は、始める地域（メッシュ 5339）の分だけ入れた。地域を広げるときに足す
