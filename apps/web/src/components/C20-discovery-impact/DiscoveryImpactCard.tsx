// see docs/ui/components/C20-discovery-impact.md
//
// 対象Discoveryの表示形式(テキストリンクのみ／リンク+簡易サマリー)と、
// 複数Valueの見せ方(行／小カード)は検討事項のため、Issue #22では
// targetVariant / valuesVariantで両案をトグル実装し、比較できる状態にする
// (docs/ui/wireframe/components/C20-discovery-impact-variants.png)。
// 固定見出し(「Impact」等)は置かず、対象Discoveryを識別情報として先頭に表示する。
import Link from "next/link";
import type { DiscoveryImpact } from "@/lib/mock-data/contribution";

export function DiscoveryImpactCard({
  impact,
  targetVariant,
  valuesVariant,
}: {
  impact: DiscoveryImpact;
  targetVariant: "link" | "summary";
  valuesVariant: "rows" | "cards";
}) {
  return (
    <article className="flex flex-col gap-2 border-t py-3 first:border-t-0 first:pt-0">
      <div>
        <Link
          href={`/discoveries/${impact.discoveryId}`}
          className="text-sm font-bold text-primary underline underline-offset-2"
        >
          {impact.discoveryTitle}
        </Link>
        {targetVariant === "summary" ? (
          <p className="text-xs text-muted-foreground">
            {impact.discoveryPlace} / {impact.discoverySubject}
          </p>
        ) : null}
      </div>

      {impact.formationImpacts.map((text) => (
        <p key={text} className="flex items-start gap-1.5 text-sm">
          <span aria-hidden>●</span>
          {text}
        </p>
      ))}

      {impact.providedValues.length > 0 ? (
        valuesVariant === "cards" ? (
          <div className="flex flex-wrap gap-2">
            {impact.providedValues.map((v) => (
              <div key={v.valueName} className="rounded-md border px-3 py-2 text-xs">
                <p className="font-semibold">「{v.valueName}」</p>
                <p className="text-muted-foreground">{v.impact}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            {impact.providedValues.map((v) => (
              <p key={v.valueName} className="flex items-start gap-1.5 text-sm">
                <span aria-hidden>◆</span>「{v.valueName}」の{v.impact}
              </p>
            ))}
          </div>
        )
      ) : null}

      {impact.reach ? (
        <div className="text-xs text-muted-foreground">
          <p>
            この価値を通じて{impact.reach.period}
            {impact.reach.count}人が「{impact.discoveryTitle}」につながりました
          </p>
          <p>集計期間: {impact.reach.period} / このDiscoveryのみの人数(他のDiscoveryとは合算しない)</p>
        </div>
      ) : null}

      {impact.valuePending ? (
        <p className="rounded-md bg-muted/60 p-2 text-xs text-muted-foreground">
          {impact.valuePending}
        </p>
      ) : null}
    </article>
  );
}
