// see docs/ui/screens/S03-discovery-detail.md, docs/api/openapi.yaml(getDiscovery・getDiscoveryPosts)
//
// S03(Discovery詳細)。API のデータで表示する(Issue #112)。
// - SSR(Server Component)で、毎回 API から取る(キャッシュしない。#89)。
// - 書き込みの操作(投稿・返信・リアクション・資料の提供)は、押すと「準備中」と出すだけにする(#89)。
// - 表現案の比較用バー・探索段階の切り替えは付けない。モックのデータでの比較は /mock/discoveries/[id] に残した。
// - 表現案は決定済みの既定値で表示する(Hero切替=サムネイル、リアクション=複数、場所=テキスト)。
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { DiscoveryDetailHero } from "@/components/C19-discovery-detail-hero/DiscoveryDetailHero";
import { ReactionButton } from "@/components/C10-reaction-button/ReactionButton";
import { DiscoveryValue } from "@/components/C12-discovery-value/DiscoveryValue";
import { DiscoveryEmergingTerms } from "@/components/C25-discovery-emerging-terms/DiscoveryEmergingTerms";
import { PostThread } from "@/components/C26-post-thread/PostThread";
import { RelatedDiscoveryRail } from "@/components/layout/RelatedDiscoveryRail";
import { RAIL_CONTAINER_CLASS, RAIL_ITEM_CLASS } from "@/components/layout/rail";
import { getDiscovery, getDiscoveryPosts } from "@/lib/api/discovery";
import {
  toDerivationMarker,
  toFinding,
  toPost,
  toRecommendation,
  toRelatedSummary,
  toValueItem,
} from "@/lib/api/discovery-view";
import { DISCOVERY_HREF_BASE } from "@/lib/discovery-href";
import { tagSearchHref } from "@/lib/mock-data/tags";
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

  const description = toDescription(discovery.body);
  const image = discovery.recommendations.find((rec) => rec.media)?.media;
  return {
    title: `${discovery.title} | ex-day`,
    description,
    openGraph: {
      type: "article",
      siteName: "ex-day",
      title: discovery.title,
      description,
      images: image ? [{ url: image.url, alt: image.alt }] : undefined,
    },
  };
}

export default async function DiscoveryDetailPage({ params }: Props) {
  const { id } = await params;
  const [discovery, thread] = await Promise.all([getDiscovery(id), getDiscoveryPosts(id)]);
  if (!discovery || !thread) notFound();

  const recommendations = discovery.recommendations.map(toRecommendation);
  const otherValues = discovery.otherValues.map(toValueItem);
  const findings = discovery.findings.map(toFinding);
  const posts = thread.posts.map((post) => toPost(post, discovery.context.now));
  const { derivedFrom, derivedTo, related } = discovery.relations;

  return (
    <NotReadyWriteActions>
      <div className="flex flex-col gap-8">
        <div className="flex flex-col gap-4">
          <h1 className="text-2xl font-bold">{discovery.title}</h1>
          <DiscoveryDetailHero key={discovery.id} recommendations={recommendations} switchStyle="thumbnail" />
          <DiscoveryEmergingTerms key={discovery.id} findings={findings} />
          {discovery.body ? (
            <p className="whitespace-pre-line text-sm leading-relaxed">{discovery.body}</p>
          ) : null}
          <ReactionButton key={discovery.id} variant="multiple" initialCount={0} initialCounts={discovery.reactions} />
          {discovery.tags.length > 0 ? (
            // Discoveryのタグ(属する声のタグと運営が付けたタグを集めたもの。DEC-0009 決定10)
            <ul aria-label="タグ" className="flex flex-wrap gap-2 text-xs">
              {discovery.tags.map((tag) => (
                <li key={tag}>
                  <Link
                    href={tagSearchHref(tag)}
                    className="rounded-full border px-2.5 py-1 text-muted-foreground hover:text-foreground"
                  >
                    #{tag}
                  </Link>
                </li>
              ))}
            </ul>
          ) : null}
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

        <PostThread
          key={discovery.id}
          initialPosts={posts}
          derivationMarkers={thread.derivationMarkers.map(toDerivationMarker)}
          derivedOriginNotice={thread.derivedOrigin}
          hrefBase={DISCOVERY_HREF_BASE}
        />

        <RelatedDiscoveryRail
          heading="この発見のもとになったDiscovery"
          items={derivedFrom.map(toRelatedSummary)}
          placeVariant="text"
          hrefBase={DISCOVERY_HREF_BASE}
          seeMoreHref={`${DISCOVERY_HREF_BASE}/${id}/related/derived-from`}
        />
        <RelatedDiscoveryRail
          heading="この発見から広がったDiscovery"
          items={derivedTo.map(toRelatedSummary)}
          placeVariant="text"
          hrefBase={DISCOVERY_HREF_BASE}
          seeMoreHref={`${DISCOVERY_HREF_BASE}/${id}/related/derived-to`}
        />
        <RelatedDiscoveryRail
          heading="関連するDiscovery"
          items={related.map(toRelatedSummary)}
          placeVariant="text"
          hrefBase={DISCOVERY_HREF_BASE}
          seeMoreHref={`${DISCOVERY_HREF_BASE}/${id}/related/related`}
        />
      </div>
    </NotReadyWriteActions>
  );
}
