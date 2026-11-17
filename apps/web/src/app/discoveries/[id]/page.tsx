// see docs/ui/screens/S03-discovery-detail.md, docs/api/openapi.yaml(getDiscovery)
//
// S03(Discovery のページ)。API のデータで表示する(Issue #112)。
// - SSR(Server Component)で、毎回 API から取る(キャッシュしない。#89)。
// - API は DB V2 の形(v0.2。#135)にした。画面はまだ作り直しておらず、今のコンポーネントに当てはめる最小の対応にしている。
//   関わる場所の地図・由来の会話・関わる企画などの区画、リアクションは、S03 の画面を作り直すときに足す。
// - 書き込みの操作は、押すと「準備中」と出すだけにする(#89)。
// - 行き先の画面がないリンクは外す(Issue #113)。
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DiscoveryDetailHero } from "@/components/C19-discovery-detail-hero/DiscoveryDetailHero";
import { DiscoveryValue } from "@/components/C12-discovery-value/DiscoveryValue";
import { DiscoveryEmergingTerms } from "@/components/C25-discovery-emerging-terms/DiscoveryEmergingTerms";
import { RelatedDiscoveryRail } from "@/components/layout/RelatedDiscoveryRail";
import { RAIL_CONTAINER_CLASS, RAIL_ITEM_CLASS } from "@/components/layout/rail";
import { getDiscovery } from "@/lib/api/discovery";
import { toDiscoverySummary, toFinding, toRecommendation, toValueItem } from "@/lib/api/discovery-view";
import { DISCOVERY_HREF_BASE } from "@/lib/discovery-href";
import { NotReadyWriteActions } from "@/lib/write-actions";

type Props = {
  params: Promise<{ id: string }>;
};

const DESCRIPTION_MAX_LENGTH = 120;

function toDescription(body: string) {
  const text = body.replace(/\s+/g, " ").trim();
  return text.length > DESCRIPTION_MAX_LENGTH ? `${text.slice(0, DESCRIPTION_MAX_LENGTH)}…` : text;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const discovery = await getDiscovery(id);
  if (!discovery) return {};

  const description = toDescription(discovery.summary);
  return {
    title: `${discovery.title} | ex-day`,
    description,
    openGraph: {
      type: "article",
      siteName: "ex-day",
      title: discovery.title,
      description,
    },
  };
}

export default async function DiscoveryDetailPage({ params }: Props) {
  const { id } = await params;
  const discovery = await getDiscovery(id);
  if (!discovery) notFound();

  const recommendations = discovery.recommendations.map(toRecommendation);
  const otherValues = discovery.otherValues.map(toValueItem);
  const findings = discovery.findings.map(toFinding);
  const { derivedFrom, derivedTo, related } = discovery.relations;

  return (
    <NotReadyWriteActions>
      <div className="flex flex-col gap-8">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-muted-foreground">
            {discovery.displayPlaces.map((place) => place.name).join("・")}
          </p>
          <h1 className="text-2xl font-bold">{discovery.title}</h1>
          {discovery.merge.status === "merged" ? (
            <p className="rounded border px-3 py-2 text-sm">
              この発見は統合されました。{discovery.merge.target ? `統合先：${discovery.merge.target.title}` : null}
            </p>
          ) : null}
          <DiscoveryDetailHero key={discovery.id} recommendations={recommendations} switchStyle="thumbnail" />
          <DiscoveryEmergingTerms key={discovery.id} findings={findings} />
          {discovery.summary ? (
            <p className="whitespace-pre-line text-sm leading-relaxed">{discovery.summary}</p>
          ) : null}
          <ul aria-label="関わる場所" className="flex flex-col gap-1 text-sm">
            {discovery.places.map((item) => (
              <li key={`${item.place.id}-${item.role}`}>
                {item.order ? `${item.order}. ` : null}
                {item.place.name}
              </li>
            ))}
          </ul>
        </div>

        {otherValues.length > 0 ? (
          <div className="flex flex-col gap-3">
            <h2 className="text-base font-semibold">その他の魅力</h2>
            <div className={RAIL_CONTAINER_CLASS}>
              {otherValues.map((item) => (
                <div key={item.id} className={`w-64 ${RAIL_ITEM_CLASS}`}>
                  <DiscoveryValue item={item} />
                </div>
              ))}
            </div>
          </div>
        ) : null}

        <RelatedDiscoveryRail
          heading="この発見のもとになったDiscovery"
          items={derivedFrom.map(toDiscoverySummary)}
          placeVariant="text"
          hrefBase={DISCOVERY_HREF_BASE}
        />
        <RelatedDiscoveryRail
          heading="この発見から広がったDiscovery"
          items={derivedTo.map(toDiscoverySummary)}
          placeVariant="text"
          hrefBase={DISCOVERY_HREF_BASE}
        />
        <RelatedDiscoveryRail
          heading="関連するDiscovery"
          items={related.map(toDiscoverySummary)}
          placeVariant="text"
          hrefBase={DISCOVERY_HREF_BASE}
        />
      </div>
    </NotReadyWriteActions>
  );
}
