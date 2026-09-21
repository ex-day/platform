// see docs/ui/components/C03-discovery-card.md
//
// 場所表示(text／map案)は検討事項のため、Issue #8ではplaceVariantで
// 両案をトグル実装し、比較できる状態にする。採用案の決定はIssue #8で人間が行う。
// 表示項目の並び順はdocs/ui/wireframe/S03/PC-ComponentVariants.pngに合わせた
// ([title] → [subject]/[value] → 場所)。
import Link from "next/link";
import { ImageIcon, MapPinIcon } from "lucide-react";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";
import { cn } from "@/lib/utils";

export function DiscoveryCard({
  item,
  placeVariant,
  className,
  layout = "a",
}: {
  item: RelatedDiscoverySummary;
  placeVariant: "text" | "map";
  className?: string;
  // S02のレイアウト比較案(Issue #31)。a: 縦型(既定) / b: Mobileのみ横型 / c: PC横型・Mobile 2列コンパクト
  layout?: "a" | "b" | "c";
}) {
  return (
    <Link
      href={`/discoveries/${item.id}`}
      className={cn(
        "flex flex-col overflow-hidden rounded-lg border transition-colors hover:border-primary",
        layout === "b" && "max-md:flex-row",
        layout === "c" && "md:flex-row",
        className,
      )}
    >
      <div className={cn(
        "flex h-40 items-center justify-center gap-2 bg-muted text-xs text-muted-foreground md:h-32",
        layout === "b" && "max-md:h-auto max-md:w-28 max-md:shrink-0",
        layout === "c" && "h-24 md:h-auto md:w-40 md:shrink-0",
      )}>
        <ImageIcon className="h-4 w-4" aria-hidden />
        {item.picture ?? "no image"}
      </div>
      <div className="flex flex-1 flex-col gap-1 p-3">
        <h4 className="text-sm font-semibold">{item.title}</h4>
        <p className="line-clamp-2 text-xs text-muted-foreground">
          {item.subject} / {item.value}
        </p>
        {placeVariant === "map" ? (
          <div className="mt-1 flex h-16 items-center justify-center gap-1 rounded bg-muted text-[11px] text-muted-foreground">
            <MapPinIcon className="h-3.5 w-3.5" aria-hidden />
            {item.place.lat.toFixed(3)}, {item.place.lng.toFixed(3)}
          </div>
        ) : (
          <div className="mt-1 flex items-center gap-1 text-[11px] text-muted-foreground">
            <MapPinIcon className="h-3.5 w-3.5" aria-hidden />
            {item.place.text}
          </div>
        )}
      </div>
    </Link>
  );
}
