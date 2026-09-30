// see docs/ui/components/C05-discovery-section.md
//
// moreHref を渡さない場合は「もっと見る」(S02への移動)を出さない。API のデータの S01 では外している(#89、#113)。
import Link from "next/link";
import { DiscoveryList } from "@/components/C04-discovery-list/DiscoveryList";
import type { DiscoverySectionData } from "@/lib/mock-data/top";
export function DiscoverySection({ section, hideTitle = false, hrefBase, moreHref }: { section: DiscoverySectionData; hideTitle?: boolean; hrefBase?: string; moreHref?: string }) {
  return <section aria-labelledby={hideTitle ? undefined : `${section.id}-title`} className="flex flex-col gap-4">{hideTitle ? null : <h2 id={`${section.id}-title`} className="text-xl font-bold">{section.title}</h2>}<DiscoveryList items={section.items} hrefBase={hrefBase} />{moreHref ? <Link href={moreHref} className="self-end text-sm font-medium underline underline-offset-4 hover:text-muted-foreground">もっと見る →</Link> : null}</section>;
}
