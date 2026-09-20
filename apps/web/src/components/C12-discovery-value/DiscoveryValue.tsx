// see docs/ui/components/C12-discovery-value.md
import { ImageIcon } from "lucide-react";
import type { DiscoveryValueItem } from "@/lib/mock-data/discovery";

export function DiscoveryValue({ item }: { item: DiscoveryValueItem }) {
  return (
    <article className="flex flex-col gap-3 rounded-lg border p-4">
      <span className="text-xs font-medium text-muted-foreground">
        {item.subject}
      </span>
      <h3 className="text-base font-semibold">{item.value}</h3>
      {item.picture ? (
        <div className="flex h-28 items-center justify-center gap-2 rounded-md bg-muted text-xs text-muted-foreground">
          <ImageIcon className="h-4 w-4" aria-hidden />
          {item.picture}
        </div>
      ) : null}
      {item.conditions.length > 0 ? (
        <ul className="flex flex-wrap gap-2 text-xs">
          {item.conditions.map((condition) => (
            <li
              key={condition.label}
              className="rounded-full bg-muted px-2 py-1 text-muted-foreground"
            >
              {condition.label}: {condition.value}
            </li>
          ))}
        </ul>
      ) : null}
      <p className="text-sm text-muted-foreground">{item.body}</p>
    </article>
  );
}
