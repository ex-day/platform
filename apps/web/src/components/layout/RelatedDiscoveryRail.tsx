// 設計書のC番号を持たない、純粋な表示構造としてのレール
// (docs/フロントエンド技術方針.md の3区分「レイアウト・構造要素」に該当)。
//
// モバイル関係Discoveryの横スクロール表示の詳細(上限件数、カード幅、
// 「さらに見る」の具体表現、遷移先)はNon-blockingな検討事項
// (docs/ui/screens/S03-discovery-detail.md 未確定事項)。ここでの値は
// Next.jsモックとして一旦確定させた仮の値であり、S02との整合確認後に
// 見直す前提。1〜2件の場合は横スクロールにせず折り返し表示にする(./rail.ts)。
import Link from "next/link";
import { DiscoveryCard } from "@/components/C03-discovery-card/DiscoveryCard";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";
import { cn } from "@/lib/utils";
import {
  RAIL_CONTAINER_CLASS,
  RAIL_ITEM_CLASS,
  WRAP_CONTAINER_CLASS,
  shouldUseRail,
} from "./rail";

const RAIL_LIMIT = 6;

export function RelatedDiscoveryRail({
  heading,
  items,
  placeVariant,
  seeMoreHref,
}: {
  heading: string;
  items: RelatedDiscoverySummary[];
  placeVariant: "text" | "map";
  seeMoreHref: string;
}) {
  if (items.length === 0) return null;

  const visible = items.slice(0, RAIL_LIMIT);
  const hasMore = items.length > RAIL_LIMIT;
  const useRail = shouldUseRail(items.length);

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-base font-semibold">{heading}</h2>
      <div className={useRail ? RAIL_CONTAINER_CLASS : WRAP_CONTAINER_CLASS}>
        {visible.map((item) => (
          <DiscoveryCard
            key={item.id}
            item={item}
            placeVariant={placeVariant}
            className={useRail ? cn("w-40", RAIL_ITEM_CLASS) : "w-40"}
          />
        ))}
        {hasMore ? (
          <Link
            href={seeMoreHref}
            className="flex w-40 shrink-0 snap-start flex-col items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground hover:border-primary hover:text-primary md:w-auto"
          >
            さらに見る
          </Link>
        ) : null}
      </div>
    </section>
  );
}
