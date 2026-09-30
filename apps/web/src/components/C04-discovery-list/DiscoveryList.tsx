// see docs/ui/components/C04-discovery-list.md
//
// layout未指定はS01と同じ(PC 4列/Mobile 縦1列)。S02のレイアウト比較案(Issue #31)は次のとおり。
// - a: PC 4列 / Mobile 縦型1列(S01と整合)
// - b: PC 3列 / Mobile 横型
// - c: PC 横型 / Mobile 2列コンパクト
import type { ReactNode } from "react";
import { DiscoveryCard } from "@/components/C03-discovery-card/DiscoveryCard";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";
import { MOCK_DISCOVERY_HREF_BASE } from "@/lib/discovery-href";

const GRID: Record<"a" | "b" | "c", string> = {
  a: "grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4",
  b: "grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3",
  c: "grid grid-cols-2 gap-3 md:grid-cols-1 md:gap-4 lg:grid-cols-2",
};

export function DiscoveryList({ items, layout = "a", extra }: { items: RelatedDiscoverySummary[]; layout?: "a" | "b" | "c"; extra?: ReactNode }) {
  return <div data-testid="discovery-list" data-layout={layout} className={GRID[layout]}>{items.map((item, index) => <DiscoveryCard key={`${item.id}-${index}`} item={item} placeVariant="text" layout={layout} hrefBase={MOCK_DISCOVERY_HREF_BASE} />)}{extra}</div>;
}
