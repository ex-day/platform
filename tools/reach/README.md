# tools/reach：寄り道の到達圏の版を作る

寄り道の到達圏（[F06](../../docs/ui/functions/F06-detour-reach.md)）の計算に使うデータ（版）を、N02（鉄道）と OSM（歩き）から作り、API が使う版を切り替える（#168。手順の決まり・性能・運用は #163）。2026-11-25 の人の判断で、取り込みは Python のコマンドにした（PoC #46 の pyosmium・psycopg の作りを引き継ぐ）。組み立ては SQL（pgRouting）で行う。

## 作るもの

| 段 | 入力 | 作るもの（DB） |
|---|---|---|
| 鉄道 | N02 の `N02-*_Station.geojson`・`N02-*_RailroadSection.geojson`（UTF-8） | 路線 `reach.line`（ないものだけ。表定速度は人が直す）、駅の場所（`place`・種類が駅）、計算の中の駅 `reach.station`・`reach.station_source`、版のグラフ `reach.node`・`reach.edge`・`reach.station_node` |
| 歩き | highway の道を切り出した `.osm.pbf` | 版の駅から場所への歩き `reach.place_walk`（片道15分まで） |

- **駅の名寄せ**：N02 の駅は、同じ路線・同じ駅名で 1km 以内のすでにある駅に結ぶ。駅のまとまり（N02 のグループ）は、結べた駅の場所か、同じ名前（「駅」を除く）で 500m 以内の種類が駅の場所に結び、なければ新しく作る（`source_dataset = 'N02'`）。年度が変わって駅コードが変わっても、駅と駅の場所は変えない。新設・廃止・名前の変更の運用は #163
- **歩き**：OSM の歩ける道（`highway` のうち motorway 系・建設中などを除き、`foot=no`・`use_sidepath` と `foot=yes` 等の無い `access=private/no` も除く）を交差点で区切ってグラフにし、つながっている点が100未満の島を除く（PoC #46。閾値は暫定）。駅のホームの重心・場所の到達点（徒歩）・場所の代表点を、300m 以内の道の点に結び、駅ごとに片道15分までたどる。道に結べない到達点は外す（DEC-0014 決定1）
- **寄り道先にしない場所**：都道府県・市区町村・町丁・字・駅
- **OSM の歩きのグラフは DB に残さない**：この処理の一時の表だけで使う（ODbL の派生のデータベースを小さくする。DEC-0014 決定1）
- 計算の決まりの値（乗換10分・歩き15分・80m/分）は `reach_build.py` の先頭にあり、API の `exday.detour.*` と同じ値にする

## 使い方

```sh
pip install -r tools/reach/requirements.txt
export EXDAY_DSN=postgresql://exday:exday@127.0.0.1:5433/exday   # 既定は compose.yaml の DB

# OSM：Geofabrik から取ってきて、使う範囲の highway の道を切り出す（ex-day/poc reach/osmwalk/extract_highway.py）
python extract_highway.py ~/Downloads/kanto-261119.osm.pbf ~/Downloads/kanto-261119-5339-highway.osm.pbf

# 版を作る（BUILDING → READY。失敗したら FAILED で、使っている版はそのまま）
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

## まだ決めていないこと（#163）

- 更新と切り戻しの手順（いつ・誰が・どの順で、失敗したときの扱い）、古い版をいつ消すか
- 応答時間・同時実行の上限、作る時間の上限
- 小さな島の閾値の確定（消す島に結び付く駅・場所・到達点の数と例を見て決める）
- 区間ごとの表定速度の上書き（`reach.speed_override`）を版を作るときに当てる（表はある。取り込みでの適用は後続）
- N02 の事業者の名前（例：「東日本旅客鉄道」）を、駅の候補で見せる短い名前（「JR東日本」）にするか
