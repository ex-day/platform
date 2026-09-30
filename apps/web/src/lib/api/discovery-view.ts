// API の型(./schema.d.ts)から、S03 のコンポーネントの props への変換(Issue #112)。
//
// コンポーネントは、モックと API の両方のデータで表示するため、API の型に寄せず、
// 今の props の型(@/lib/mock-data/discovery)のまま使う。API の型との違いはここで吸収する。
import type { components } from "./schema";
import type {
  DerivationMarker,
  DiscoveryFinding,
  DiscoveryPost,
  DiscoveryRecommendation,
  DiscoveryValueItem,
  RelatedDiscoverySummary,
} from "@/lib/mock-data/discovery";
import type { DraftMedia } from "@/lib/mock-data/media";

type Schemas = components["schemas"];

export function toRecommendation(rec: Schemas["Recommendation"]): DiscoveryRecommendation {
  return {
    id: rec.valueId,
    message: rec.message,
    participationMessage: rec.participationMessage,
    media: rec.media ? { alt: rec.media.alt, url: rec.media.url } : undefined,
  };
}

export function toValueItem(item: Schemas["ValueItem"]): DiscoveryValueItem {
  return {
    id: item.id,
    subject: item.subject,
    value: item.value,
    picture: item.picture?.alt,
    pictureUrl: item.picture?.url,
    body: item.body,
    conditions: item.conditions.map((c) => ({ label: c.label, value: c.text })),
  };
}

export function toFinding(finding: Schemas["Finding"]): DiscoveryFinding {
  return {
    id: finding.id,
    kind: finding.kind,
    text: finding.text,
    backedBy: finding.backedBy,
    reactions: finding.reactions ?? {},
  };
}

export function toRelatedSummary(card: Schemas["DiscoveryCard"]): RelatedDiscoverySummary {
  return {
    id: card.id,
    title: card.title,
    subject: card.subject,
    value: card.value,
    picture: card.picture?.alt,
    pictureUrl: card.picture?.url,
    place: { text: card.place.name },
  };
}

function toMedia(media: Schemas["MediaItem"]): DraftMedia {
  return {
    id: media.id,
    name: media.name,
    kind: media.kind,
    previewUrl: media.image?.url,
    sourceStatus: media.source.status,
    sourceType: media.source.type,
    sourceName: media.source.name,
    flaggedInappropriate: false,
  };
}

/**
 * 投稿日時を「n日前」などの相対表現にする。基準の「今」は API の context.now を使い、
 * 開発用の設定で「今」を固定した場合も、画面と API で食い違わないようにする。
 */
export function formatRelativeTime(postedAt: string, now: string): string {
  const minutes = Math.floor((Date.parse(now) - Date.parse(postedAt)) / 60_000);
  if (Number.isNaN(minutes)) return "";
  if (minutes < 1) return "たった今";
  if (minutes < 60) return `${minutes}分前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}時間前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}日前`;
  if (days < 365) return `${Math.floor(days / 30)}か月前`;
  return `${Math.floor(days / 365)}年前`;
}

export function toPost(post: Schemas["Post"], now: string): DiscoveryPost {
  return {
    id: post.id,
    author: post.authorName,
    postedAtLabel: formatRelativeTime(post.postedAt, now),
    body: post.body,
    media: post.media.map(toMedia),
    replyTo: post.replyTo?.postId,
    continuedIn: post.continuedIn,
    reactionCount: post.reactionCount,
  };
}

export function toDerivationMarker(marker: Schemas["DerivationMarker"]): DerivationMarker {
  return { afterPostId: marker.afterPostId, discovery: marker.discovery };
}
