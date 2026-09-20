// see docs/ui/screens/S03-discovery-detail.md
//
// モック用の仮ルート。[id]はモック用の仮ID(例: sample-1, sample-2, sample-3。
// 未知のidはsample-1相当の内容にフォールバックする)であり、本番のDiscovery
// 識別子の形式(docs/ex-day_logical_entity_design.md 参照)を先取りするものではない。
//
// 検討中の複数表現案はsearchParamsのクエリで切り替える(Issue #8のNon-blockingな
// 要検討事項)。画面上部の[Mock比較用]バーから切り替えて比較できる。
// - hero: Hero切替UI。"arrow"(矢印) | "thumbnail"(サムネイル)。既定値は"arrow"。
// - reaction: リアクションボタン表現。"single"(単一) | "multiple"(複数)。既定値は"single"。
// - place: 場所表示。"text" | "map"。既定値は"text"。
// 各案の採用判断はIssue #8で人間が行う(docs/development/ai-collaboration.md §3)。
import Link from "next/link";
import { DiscoveryDetailHero } from "@/components/C19-discovery-detail-hero/DiscoveryDetailHero";
import { ReactionButton } from "@/components/C10-reaction-button/ReactionButton";
import { DiscoveryValue } from "@/components/C12-discovery-value/DiscoveryValue";
import { RelatedDiscoveryRail } from "@/components/layout/RelatedDiscoveryRail";
import { getDiscoveryById } from "@/lib/mock-data/discovery";

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

const HERO_OPTIONS = ["arrow", "thumbnail"] as const;
const REACTION_OPTIONS = ["single", "multiple"] as const;
const PLACE_OPTIONS = ["text", "map"] as const;

function pickParam<T extends readonly string[]>(
  value: string | string[] | undefined,
  options: T,
  fallback: T[number],
): T[number] {
  const v = Array.isArray(value) ? value[0] : value;
  return (options as readonly string[]).includes(v ?? "")
    ? (v as T[number])
    : fallback;
}

export default async function DiscoveryDetailPage({
  params,
  searchParams,
}: Props) {
  const { id } = await params;
  const sp = await searchParams;

  const hero = pickParam(sp.hero, HERO_OPTIONS, "arrow");
  const reaction = pickParam(sp.reaction, REACTION_OPTIONS, "single");
  const place = pickParam(sp.place, PLACE_OPTIONS, "text");

  const discovery = getDiscoveryById(id);

  const compareHref = (key: "hero" | "reaction" | "place", value: string) => {
    const next = new URLSearchParams();
    next.set("hero", key === "hero" ? value : hero);
    next.set("reaction", key === "reaction" ? value : reaction);
    next.set("place", key === "place" ? value : place);
    return `?${next.toString()}`;
  };

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        <span className="font-medium">[Mock比較用]</span>
        <span>
          Hero:
          {HERO_OPTIONS.map((opt) => (
            <Link
              key={opt}
              href={compareHref("hero", opt)}
              className={
                opt === hero
                  ? "mx-1 font-semibold text-foreground underline"
                  : "mx-1 hover:underline"
              }
            >
              {opt}
            </Link>
          ))}
        </span>
        <span>
          リアクション:
          {REACTION_OPTIONS.map((opt) => (
            <Link
              key={opt}
              href={compareHref("reaction", opt)}
              className={
                opt === reaction
                  ? "mx-1 font-semibold text-foreground underline"
                  : "mx-1 hover:underline"
              }
            >
              {opt}
            </Link>
          ))}
        </span>
        <span>
          場所:
          {PLACE_OPTIONS.map((opt) => (
            <Link
              key={opt}
              href={compareHref("place", opt)}
              className={
                opt === place
                  ? "mx-1 font-semibold text-foreground underline"
                  : "mx-1 hover:underline"
              }
            >
              {opt}
            </Link>
          ))}
        </span>
      </div>

      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">{discovery.title}</h1>
        <DiscoveryDetailHero
          recommendations={discovery.recommendations}
          switchStyle={hero}
        />
        <p className="whitespace-pre-line text-sm leading-relaxed">
          {discovery.body}
        </p>
        <ReactionButton variant={reaction} initialCount={discovery.reactionCount} />
      </div>

      {discovery.otherValues.length > 0 ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-base font-semibold">その他の魅力</h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {discovery.otherValues.map((item) => (
              <DiscoveryValue key={item.id} item={item} />
            ))}
          </div>
        </div>
      ) : null}

      {/*
        S06 知識・疑問一覧のURLは screen-list.md に未記載のため、
        Discovery配下の仮パスとして扱う(Non-blocking)。
      */}
      <Link
        href={`/discovery/${id}/contributions`}
        className="text-sm font-medium text-primary underline underline-offset-2"
      >
        知識・疑問一覧を見る
      </Link>

      <RelatedDiscoveryRail
        heading="このDiscoveryのもとになった発見"
        items={discovery.derivedFrom}
        placeVariant={place}
        seeMoreHref={`/discovery/${id}/related/derived-from`}
      />
      <RelatedDiscoveryRail
        heading="この発見から広がったDiscovery"
        items={discovery.derivedTo}
        placeVariant={place}
        seeMoreHref={`/discovery/${id}/related/derived-to`}
      />
      <RelatedDiscoveryRail
        heading="関連するDiscovery"
        items={discovery.related}
        placeVariant={place}
        seeMoreHref={`/discovery/${id}/related/related`}
      />
    </div>
  );
}
