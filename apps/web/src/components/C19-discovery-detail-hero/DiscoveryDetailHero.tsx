// see docs/ui/components/C19-discovery-detail-hero.md
//
// Recommendation切替UIはサムネイルを採用済み(C19検討事項)。Issue #8で比較した
// 矢印案もswitchStyleで表示できるよう残している。
//
// Recommendationが0件の場合も、Heroは非表示にせず標準Heroを表示する(Issue #50で決定。
// PC-HeroVariants.pngのD「非表示」・E「最小表示」は不採用)。標準Heroのデザイン・文言は
// Issue #52で検討するため、ここでの表示は仮のもの。現在成立しないValueから補完せず、
// 対象を推測した画像も用いない。
"use client";

import { useState } from "react";
import { ChevronLeftIcon, ChevronRightIcon, ImageIcon, SearchIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import type { DiscoveryRecommendation } from "@/lib/mock-data/discovery";

export function DiscoveryDetailHero({
  recommendations,
  switchStyle,
}: {
  recommendations: DiscoveryRecommendation[];
  switchStyle: "arrow" | "thumbnail";
}) {
  const [index, setIndex] = useState(0);

  if (recommendations.length === 0) {
    // 標準Hero(仮表示)。文言・デザインはIssue #52で検討する
    return (
      <section
        aria-label="今伝えたい価値"
        className="flex min-h-48 flex-col items-center justify-center gap-3 rounded-xl border bg-muted p-6 text-center"
      >
        <SearchIcon className="h-8 w-8 text-muted-foreground" aria-hidden />
        <p className="text-lg font-semibold">いま、情報を集めています</p>
        <p className="text-sm text-muted-foreground">
          知っていることや手元の資料があれば、コメントで教えてください
        </p>
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
