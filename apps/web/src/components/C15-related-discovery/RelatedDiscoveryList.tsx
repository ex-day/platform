// see docs/ui/components/C15-related-discovery.md
//
// 表示形式(名称中心のリンク行／C03カード再利用)は検討事項のため、
// Issue #22ではvariantで両案をトグル実装し、比較できる状態にする
// (docs/ui/wireframe/components/C15-related-discovery-variants.png)。
// 見出し文言「関連するDiscovery」はワイヤーフレーム上もTBD表記のため、
// 暫定文言として使用する。
import Link from "next/link";
import { ChevronRightIcon, ImageIcon } from "lucide-react";
import { DiscoveryCard } from "@/components/C03-discovery-card/DiscoveryCard";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";

export function RelatedDiscoveryList({
  items,
  variant,
}: {
  items: RelatedDiscoverySummary[];
  variant: "link" | "card";
}) {
  if (items.length === 0) return null;

  return (
    <section className="flex flex-col gap-3 rounded-lg border p-4">
      <h2 className="text-base font-semibold">
        関連するDiscovery <span className="text-xs font-normal text-muted-foreground">(見出し文言はTBD)</span>
      </h2>
      {variant === "card" ? (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {items.map((item) => (
            <DiscoveryCard key={item.id} item={item} placeVariant="text" />
          ))}
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {items.map((item) => (
            <Link
              key={item.id}
              href={`/discoveries/${item.id}`}
              className="flex items-center gap-3 rounded-md border p-2 transition-colors hover:border-primary"
            >
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded bg-muted text-muted-foreground">
                <ImageIcon className="h-4 w-4" aria-hidden />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{item.title}</p>
                <p className="truncate text-xs text-muted-foreground">
                  {item.subject} / {item.value}
                </p>
              </div>
              <ChevronRightIcon className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
