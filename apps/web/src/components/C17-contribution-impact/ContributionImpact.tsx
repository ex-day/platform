// see docs/ui/components/C17-contribution-impact.md
//
// C20が複数ある場合の見せ方(縦に並べる／要約+先頭のみ展開)は検討事項のため、
// Issue #22ではmultiVariantで両案をトグル実装し、比較できる状態にする
// (docs/ui/wireframe/components/C17-contribution-impact-states.png)。
// 並び順は確定していない順位・Impactの大小を示すものとして扱わない。
"use client";

import { useState } from "react";
import { DiscoveryImpactCard } from "@/components/C20-discovery-impact/DiscoveryImpactCard";
import type { DiscoveryImpact } from "@/lib/mock-data/contribution";

export function ContributionImpact({
  discoveryImpacts,
  multiVariant,
  targetVariant,
  valuesVariant,
  className,
}: {
  discoveryImpacts: DiscoveryImpact[];
  multiVariant: "stacked" | "summary";
  targetVariant: "link" | "summary";
  valuesVariant: "rows" | "cards";
  className?: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const useSummary = multiVariant === "summary" && discoveryImpacts.length > 1;
  const visible = useSummary && !expanded ? discoveryImpacts.slice(0, 1) : discoveryImpacts;
  const hiddenCount = discoveryImpacts.length - visible.length;

  return (
    <section className={className}>
      <h2 className="text-base font-semibold">この投稿から見つかった価値</h2>
      {useSummary ? (
        <p className="mt-1 text-xs text-muted-foreground">
          {discoveryImpacts.length}件のDiscoveryで価値として利用されました(要約文言はTBD)
        </p>
      ) : null}
      {discoveryImpacts.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground">現在確認中です。</p>
      ) : (
        <div className="mt-2 flex flex-col rounded-lg border px-4">
          {visible.map((impact) => (
            <DiscoveryImpactCard
              key={impact.discoveryId}
              impact={impact}
              targetVariant={targetVariant}
              valuesVariant={valuesVariant}
            />
          ))}
        </div>
      )}
      {hiddenCount > 0 ? (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="mt-2 text-sm text-primary underline underline-offset-2"
        >
          他{hiddenCount}件を表示
        </button>
      ) : null}
    </section>
  );
}
