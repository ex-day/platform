// see docs/ui/components/C25-discovery-emerging-terms.md, DEC-0005 Decision 9
//
// 語句タップ時の挙動・表示上限・並び順はC25の検討事項のため、モックでは
// 受け取った順にすべて表示するだけとする。
export function DiscoveryEmergingTerms({ terms }: { terms: string[] }) {
  if (terms.length === 0) return null;

  return (
    <section className="flex flex-col gap-2" aria-labelledby="emerging-terms-heading">
      <h2 id="emerging-terms-heading" className="text-sm font-semibold">
        わかってきたこと
        <span className="ml-1 text-xs font-normal text-muted-foreground">（未確定）</span>
      </h2>
      <ul className="flex flex-wrap gap-2">
        {terms.map((term) => (
          <li
            key={term}
            className="rounded-full border border-dashed px-3 py-1 text-xs text-muted-foreground"
          >
            {term}
          </li>
        ))}
      </ul>
    </section>
  );
}
