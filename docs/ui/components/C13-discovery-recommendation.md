# C13 discovery recommendation（廃止）

## 状態
S03 Heroレビュー（2026-09-19）により表示責務を[Discovery詳細 Hero](C19-discovery-detail-hero.md)へ統合し、独立Componentとして廃止する。C13のIDは保持し、振り直し・再利用しない。本ファイルは既存参照のために残す。

## 判断理由と参照確認
- リポジトリ全体の参照を確認した結果、利用画面はS03のみだった。他はComponent一覧と過去のレビュー記録であり、実装上の利用は見つからなかった。
- 従来の責務は、渡されたRecommendationのValueをC12で表示し、おすすめ理由を添えることだった。価値を伝える責務はHeroと重複するため統合する。ただし、C12の属性表示や独立した「おすすめ理由」欄をHeroへそのまま移植する意味ではない。Heroは評価済みRecommendationをユーザー向けの価値表現として提示する。
- S03の参照をHeroへ変更し、Component一覧に廃止・統合先を記載する。過去レビューは当時の記録として変更しない。
- ドメイン概念のRecommendationとC12は継続する。廃止対象はこの表示Componentである。
