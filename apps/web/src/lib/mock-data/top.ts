// see docs/ui/screens/S01-top.md, docs/ui/functions/F01-discovery-sections.md
// F01のSection決定条件は未定義のため、Issue #14で指定されたA/B/Cのダミーに限定する。
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";

export type DiscoverySectionData = { id: string; title: string; items: RelatedDiscoverySummary[] };
const TITLES = ["静かな夕景が広がる城跡", "川沿いを歩く朝の散策路", "古い商店街の小さな喫茶店", "山あいに残る謎の石碑", "海風を感じる展望デッキ", "雨の日に響く古民家の音"] as const;
function makeItems(sectionIndex: number): RelatedDiscoverySummary[] {
  return Array.from({ length: 4 }, (_, index) => {
    const n = sectionIndex * 4 + index;
    return { id: `sample-${(n % 3) + 1}`, title: TITLES[n % TITLES.length], subject: ["景色", "散策", "建物", "歴史"][index], value: ["季節ごとの魅力", "短時間で楽しめる", "地域の物語", "まだ知られていない発見"][index], picture: `Discovery写真 ${n + 1}`, place: { text: ["東京都西部", "長野県北部", "京都府南部", "広島県東部"][index], lat: 35 + n / 100, lng: 136 + n / 100 } };
  });
}
export const TOP_SECTIONS: DiscoverySectionData[] = ["A", "B", "C"].map((label, index) => ({ id: `section-${label.toLowerCase()}`, title: `Section名 ${label}`, items: makeItems(index) }));
