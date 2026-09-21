// see docs/ui/screens/S02-discovery-list.md
// S02モック用のダミーデータ。既定条件・既定半径・拡大範囲の値は仮置きであり、確定値ではない。
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";
import { TOP_SECTIONS } from "@/lib/mock-data/top";

export const STATES = ["normal", "few", "expanded", "zero", "loading", "error"] as const;
export type ListState = (typeof STATES)[number];
export const ENTRIES = ["section", "search", "default"] as const;
export type Entry = (typeof ENTRIES)[number];
export const LAYOUTS = ["a", "b", "c"] as const;
export type ListLayout = (typeof LAYOUTS)[number];

// 「少数件」と判定する件数の上限(仮置き)。判定条件(件数・PC/Mobile・レイアウト・検索範囲による違い)は実装時に確定する。
export const FEW_COUNT_MAX = 3;
export const DEFAULT_RADIUS_KM = 5; // 仮置き
export const EXPANDED_RADIUS_KM = 20; // 仮置き
export const DEFAULT_AREA = "東京都千代田区"; // 現在地を取得できない場合の既定エリア(仮置き)

const TITLES = ["静かな夕景が広がる城跡", "川沿いを歩く朝の散策路", "古い商店街の小さな喫茶店", "山あいに残る謎の石碑", "海風を感じる展望デッキ", "雨の日に響く古民家の音"];
const PLACES = ["東京都西部", "長野県北部", "京都府南部", "広島県東部"];

export function makeDiscoveries(count: number): RelatedDiscoverySummary[] {
  return Array.from({ length: count }, (_, n) => ({
    id: `sample-${(n % 3) + 1}`,
    title: `${TITLES[n % TITLES.length]} ${n + 1}`,
    subject: ["景色", "散策", "建物", "歴史"][n % 4],
    value: ["季節ごとの魅力", "短時間で楽しめる", "地域の物語", "まだ知られていない発見"][n % 4],
    picture: `Discovery写真 ${n + 1}`,
    place: { text: PLACES[n % 4], lat: 35 + n / 100, lng: 136 + n / 100 },
  }));
}

export function sectionTitle(sectionId: string | undefined) {
  return TOP_SECTIONS.find((s) => s.id === sectionId)?.title;
}
