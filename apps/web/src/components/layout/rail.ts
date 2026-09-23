// S03の付随的な情報(その他の魅力=C12、派生元/派生した/関連Discovery=C03)で共用する
// 「モバイルは横スクロール・PC(md以上)はグリッド」の表示方式
// (docs/ui/screens/S03-discovery-detail.md 構成方針)。
//
// 関係Discovery(RelatedDiscoveryRail)は、件数が少ない場合(RAIL_MIN_COUNT未満)は
// 横スクロールらしい表現にせず折り返し表示にする。その他の魅力はWireframeに合わせ
// 件数によらず横スクロールとする。
// 閾値・列数はNext.jsモックとしての仮の値(S03 未確定事項)。

export const RAIL_MIN_COUNT = 3;

export const RAIL_CONTAINER_CLASS =
  "flex snap-x gap-3 overflow-x-auto pb-1 md:grid md:grid-cols-3 md:overflow-visible lg:grid-cols-4";

export const WRAP_CONTAINER_CLASS = "flex flex-wrap gap-3";

/** 横スクロール時の各アイテム。幅は呼び出し側でw-*を指定し、md以上はグリッドに従う */
export const RAIL_ITEM_CLASS = "shrink-0 snap-start md:w-auto";

export function shouldUseRail(count: number): boolean {
  return count >= RAIL_MIN_COUNT;
}
