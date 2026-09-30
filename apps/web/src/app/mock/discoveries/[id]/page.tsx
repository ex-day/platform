// see docs/ui/screens/S03-discovery-detail.md, docs/ui/wireframe/S03/*.png
//
// モック用のルート(/mock/discoveries/[id])。Issue #112で、/discoveries/[id]をAPIのデータで
// 表示するようにしたため、表現案の比較用に、モックのデータのままここへ移した。
// 画面内のリンク(関連Discovery・派生の目印など)も/mock/discoveries/…の中で移る。
//
// [id]はモック用の仮ID(例: sample-1, sample-2, sample-3。
// 未知のidはsample-1相当の内容にフォールバックする)であり、本番のDiscovery
// 識別子の形式(docs/ex-day_logical_entity_design.md 参照)を先取りするものではない。
//
// 検討中の複数表現案はsearchParamsのクエリで切り替える(Issue #8のNon-blockingな
// 要検討事項)。画面上部の[Mock比較用]バーから切り替えて比較できる。
// - hero: Hero切替UI。"arrow"(矢印) | "thumbnail"(サムネイル)。既定値は"thumbnail"。
// - reaction: リアクションボタン表現。"single"(単一) | "multiple"(複数)。既定値は"multiple"。
// - place: 場所表示。"text" | "map"。既定値は"text"。
// 既定値は決定済みの方針に合わせている(サムネイル=C19、複数リアクション・場所text=
// S03構成方針、Issue #50のレビューで既決と確認)。他の案は比較用に残している。
//
// DEC-0005・Issue #48/#50により、Hero直後にC25(わかってきたこと)を置き、
// 従来のS06への導線をC26(みんなの声)の埋め込みに置き換えた。
// Issue #67により、C25を説・価値と種類ごとのリアクション、C26を「みんなの声」(返信先の引用・タグ・
// 派生の誘導)に改め、Discoveryのタグを表示する。派生の誘導はsample-1(目印・注記・返信時の提案)と
// sample-3(派生先の冒頭の経緯)で確認できる。
//
// Issue #50: 探索段階ごとの見え方を確認するため、[Mock比較用]バーの「状態」で
// 探索開始直後(exploring-start)／探索中(exploring)／推薦可能(sample-1)を切り替える。
// 段階の呼び分けはモック上のもので、状態の定義はIssue #36/#46で行う。
// - origin: 探索開始直後の起点資料。"none" | "photo"。既定値は"none"。
//   写真ありは起点の写真をHeroに利用し、なしは標準Hero(仮表示、Issue #52)を表示する。
//   Heroは状態にかかわらず常に表示する(Issue #50で決定。比較案D/Eは不採用)。
// - related: 探索中の関連Discovery。"none" | "some"。既定値は"none"。
import Link from "next/link";
import { DiscoveryDetailHero } from "@/components/C19-discovery-detail-hero/DiscoveryDetailHero";
import { ReactionButton } from "@/components/C10-reaction-button/ReactionButton";
import { DiscoveryValue } from "@/components/C12-discovery-value/DiscoveryValue";
import { DiscoveryEmergingTerms } from "@/components/C25-discovery-emerging-terms/DiscoveryEmergingTerms";
import { PostThread } from "@/components/C26-post-thread/PostThread";
import { tagSearchHref } from "@/lib/mock-data/tags";
import { RelatedDiscoveryRail } from "@/components/layout/RelatedDiscoveryRail";
import { RAIL_CONTAINER_CLASS, RAIL_ITEM_CLASS } from "@/components/layout/rail";
import { getDiscoveryById } from "@/lib/mock-data/discovery";
import { MOCK_DISCOVERY_HREF_BASE } from "@/lib/discovery-href";

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

const HERO_OPTIONS = ["arrow", "thumbnail"] as const;
const REACTION_OPTIONS = ["single", "multiple"] as const;
const PLACE_OPTIONS = ["text", "map"] as const;
const ORIGIN_OPTIONS = ["none", "photo"] as const;
const RELATED_OPTIONS = ["none", "some"] as const;

const STAGES = [
  { id: "exploring-start", label: "探索開始直後" },
  { id: "exploring", label: "探索中" },
  { id: "sample-1", label: "推薦可能" },
  { id: "sample-2", label: "疑問（説が並ぶ）" },
  { id: "sample-3", label: "派生先" },
] as const;

const OPTION_LABELS: Record<string, string> = {
  none: "なし",
  photo: "写真あり",
  some: "あり",
};

type ParamKey = "hero" | "reaction" | "place" | "origin" | "related";

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

function toggleClass(active: boolean) {
  return active ? "mx-1 font-semibold text-foreground underline" : "mx-1 hover:underline";
}

export default async function DiscoveryDetailPage({
  params,
  searchParams,
}: Props) {
  const { id } = await params;
  const sp = await searchParams;

  const current: Record<ParamKey, string> = {
    hero: pickParam(sp.hero, HERO_OPTIONS, "thumbnail"),
    reaction: pickParam(sp.reaction, REACTION_OPTIONS, "multiple"),
    place: pickParam(sp.place, PLACE_OPTIONS, "text"),
    origin: pickParam(sp.origin, ORIGIN_OPTIONS, "none"),
    related: pickParam(sp.related, RELATED_OPTIONS, "none"),
  };
  const hero = current.hero as (typeof HERO_OPTIONS)[number];
  const reaction = current.reaction as (typeof REACTION_OPTIONS)[number];
  const place = current.place as (typeof PLACE_OPTIONS)[number];

  const discovery = getDiscoveryById(id, {
    originPhoto: current.origin === "photo",
    withRelated: current.related === "some",
  });

  const compareHref = (key: ParamKey, value: string, path = "") => {
    const next = new URLSearchParams({ ...current, [key]: value });
    return `${path}?${next.toString()}`;
  };

  const renderToggle = (label: string, key: ParamKey, options: readonly string[]) => (
    <span>
      {label}:
      {options.map((opt) => (
        <Link key={opt} href={compareHref(key, opt)} className={toggleClass(opt === current[key])}>
          {OPTION_LABELS[opt] ?? opt}
        </Link>
      ))}
    </span>
  );

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        <span className="font-medium">[Mock比較用]</span>
        <span>
          状態:
          {STAGES.map((stage) => (
            <Link
              key={stage.id}
              href={compareHref("hero", current.hero, `${MOCK_DISCOVERY_HREF_BASE}/${stage.id}`)}
              className={toggleClass(stage.id === id)}
            >
              {stage.label}
            </Link>
          ))}
        </span>
        {id === "exploring-start" ? renderToggle("起点の資料", "origin", ORIGIN_OPTIONS) : null}
        {id === "exploring" ? renderToggle("関連Discovery", "related", RELATED_OPTIONS) : null}
        {renderToggle("Hero切替", "hero", HERO_OPTIONS)}
        {renderToggle("リアクション", "reaction", REACTION_OPTIONS)}
        {renderToggle("場所", "place", PLACE_OPTIONS)}
      </div>

      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">{discovery.title}</h1>
        <DiscoveryDetailHero
          // 比較用クエリの切替で選択中のRecommendationをリセットする
          key={`${discovery.id}-${current.origin}`}
          recommendations={discovery.recommendations}
          switchStyle={hero}
        />
        <DiscoveryEmergingTerms findings={discovery.findings} />
        {discovery.body ? (
          <p className="whitespace-pre-line text-sm leading-relaxed">
            {discovery.body}
          </p>
        ) : null}
        <ReactionButton variant={reaction} initialCount={discovery.reactionCount} />
        {discovery.tags.length > 0 ? (
          // Discoveryのタグ(属する声のタグと運営が付けたタグを集めたもの。DEC-0009 決定10)
          <ul aria-label="タグ" className="flex flex-wrap gap-2 text-xs">
            {discovery.tags.map((tag) => (
              <li key={tag}>
                <Link href={tagSearchHref(tag)} className="rounded-full border px-2.5 py-1 text-muted-foreground hover:text-foreground">
                  #{tag}
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

      {discovery.otherValues.length > 0 ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-base font-semibold">その他の魅力</h2>
          {/* Issue #48/#50のWireframeに合わせ、件数によらずモバイルは横スライドとする */}
          <div className={RAIL_CONTAINER_CLASS}>
            {discovery.otherValues.map((item) => (
              <div key={item.id} className={`w-64 ${RAIL_ITEM_CLASS}`}>
                <DiscoveryValue item={item} />
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <PostThread
        // 比較用クエリの切替で声の初期データが変わるため、状態をリセットする
        key={`${discovery.id}-${current.origin}`}
        initialPosts={discovery.posts}
        derivationMarkers={discovery.derivationMarkers}
        derivedOriginNotice={discovery.derivedOriginNotice}
        hrefBase={MOCK_DISCOVERY_HREF_BASE}
      />

      <RelatedDiscoveryRail
        heading="この発見のもとになったDiscovery"
        items={discovery.derivedFrom}
        placeVariant={place}
        hrefBase={MOCK_DISCOVERY_HREF_BASE}
        seeMoreHref={`${MOCK_DISCOVERY_HREF_BASE}/${id}/related/derived-from`}
      />
      <RelatedDiscoveryRail
        heading="この発見から広がったDiscovery"
        items={discovery.derivedTo}
        placeVariant={place}
        hrefBase={MOCK_DISCOVERY_HREF_BASE}
        seeMoreHref={`${MOCK_DISCOVERY_HREF_BASE}/${id}/related/derived-to`}
      />
      <RelatedDiscoveryRail
        heading="関連するDiscovery"
        items={discovery.related}
        placeVariant={place}
        hrefBase={MOCK_DISCOVERY_HREF_BASE}
        seeMoreHref={`${MOCK_DISCOVERY_HREF_BASE}/${id}/related/related`}
      />
    </div>
  );
}
