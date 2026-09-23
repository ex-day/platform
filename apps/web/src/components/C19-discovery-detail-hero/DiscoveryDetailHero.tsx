// see docs/ui/components/C19-discovery-detail-hero.md
//
// Recommendation切替UI(矢印案／サムネイル案)は検討事項のため、Issue #8では
// switchStyleで両案をトグル実装し、比較できる状態にする。採用案の決定は
// Issue #8で人間が行う。
//
// Recommendationが0件の場合のHero表示は別の検討事項であり、現在成立しない
// Valueからの補完はしない方針(docs/ui/screens/S03-discovery-detail.md)。
// docs/ui/wireframe/S03/PC-HeroVariants.pngのD(非表示)/E(最小表示)の2案のうち、
// モックではD(Hero自体を表示しない)を既定とし、Non-blockingとして引き継ぐ。
// Issue #50で探索開始直後のHeroを比較するため、emptyStyle="minimal"でEも表示できる。
"use client";

import { useState } from "react";
import { ChevronLeftIcon, ChevronRightIcon, ImageIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import type { DiscoveryRecommendation } from "@/lib/mock-data/discovery";

export function DiscoveryDetailHero({
  recommendations,
  switchStyle,
  emptyStyle = "hidden",
}: {
  recommendations: DiscoveryRecommendation[];
  switchStyle: "arrow" | "thumbnail";
  /** Recommendation0件時の表示。"hidden"=比較案D(非表示) | "minimal"=比較案E(最小表示) */
  emptyStyle?: "hidden" | "minimal";
}) {
  const [index, setIndex] = useState(0);

  if (recommendations.length === 0) {
    if (emptyStyle === "hidden") return null;
    // 文言はPC-HeroVariants.pngの例示であり確定ではない
    return (
      <section
        aria-label="今伝えたい価値"
        className="rounded-xl border border-dashed px-6 py-5 text-center text-sm text-muted-foreground"
      >
        この発見のおすすめは準備中です
      </section>
    );
  }

  const current = recommendations[Math.min(index, recommendations.length - 1)];
  const hasMultiple = recommendations.length > 1;

  return (
    <section
      aria-label="今伝えたい価値"
      className="overflow-hidden rounded-xl border"
    >
      <div className="flex min-h-48 flex-col justify-end gap-3 bg-muted p-6">
        {current.media ? (
          <div className="mb-2 flex h-40 items-center justify-center gap-2 rounded-md bg-background/60 text-sm text-muted-foreground">
            <ImageIcon className="h-5 w-5" aria-hidden />
            {current.media.alt}
          </div>
        ) : null}
        <p className="text-xl font-semibold leading-snug">{current.message}</p>
        {current.participationMessage ? (
          <p className="text-sm text-muted-foreground">
            {current.participationMessage}
          </p>
        ) : null}
      </div>
      {hasMultiple ? (
        <div className="flex items-center justify-between gap-3 border-t bg-background px-4 py-2">
          {switchStyle === "arrow" ? (
            <>
              <button
                type="button"
                onClick={() =>
                  setIndex(
                    (i) => (i - 1 + recommendations.length) % recommendations.length,
                  )
                }
                aria-label="前のおすすめ"
                className="rounded-full p-1.5 hover:bg-accent"
              >
                <ChevronLeftIcon className="h-4 w-4" aria-hidden />
              </button>
              <div className="flex gap-1.5">
                {recommendations.map((rec, i) => (
                  <span
                    key={rec.id}
                    className={cn(
                      "h-1.5 w-1.5 rounded-full",
                      i === index ? "bg-foreground" : "bg-muted-foreground/40",
                    )}
                  />
                ))}
              </div>
              <button
                type="button"
                onClick={() => setIndex((i) => (i + 1) % recommendations.length)}
                aria-label="次のおすすめ"
                className="rounded-full p-1.5 hover:bg-accent"
              >
                <ChevronRightIcon className="h-4 w-4" aria-hidden />
              </button>
            </>
          ) : (
            <div className="flex w-full gap-2 overflow-x-auto">
              {recommendations.map((rec, i) => (
                <button
                  key={rec.id}
                  type="button"
                  onClick={() => setIndex(i)}
                  aria-current={i === index}
                  className={cn(
                    "flex h-12 w-16 shrink-0 items-center justify-center rounded-md border text-[11px] text-muted-foreground",
                    i === index
                      ? "border-primary bg-primary/10 text-primary"
                      : "border-input",
                  )}
                >
                  {rec.media ? (
                    <ImageIcon className="h-4 w-4" aria-hidden />
                  ) : (
                    `案${i + 1}`
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      ) : null}
    </section>
  );
}
