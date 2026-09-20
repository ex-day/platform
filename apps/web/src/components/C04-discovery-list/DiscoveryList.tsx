// see docs/ui/components/C04-discovery-list.md
import { DiscoveryCard } from "@/components/C03-discovery-card/DiscoveryCard";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";
export function DiscoveryList({ items }: { items: RelatedDiscoverySummary[] }) {
  return <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">{items.map((item, index) => <DiscoveryCard key={`${item.id}-${index}`} item={item} placeVariant="text" />)}</div>;
}
