// see docs/ui/screens/S05-contribution-detail.md, docs/ui/wireframe/screens/S05/*.png
//
// モック用の仮ルート。[id]はモック用の仮ID(例: sample-1, sample-2, sample-3。
// 未知のidはsample-1相当の内容にフォールバックする)。
//
// 閲覧コンテキストの切替、および検討中の複数表現案はsearchParamsのクエリで
// 切り替える(Issue #22)。画面上部の[Mock比較用]バーから切り替えて比較できる。
// 各案の採用判断はIssue #22で人間が行う(docs/development/ai-collaboration.md §3)。
// - ctx: 閲覧コンテキスト。"normal"(通常閲覧) | "own"(自分の投稿の確認)。既定値は"normal"。
// - owner: ctx=own時、投稿者本人か。"self" | "other"。既定値は"self"。
//   本人でない場合、C16/C17/C18に加えC10の表示も未定義のため(Non-blocking #3)、
//   モックではC10も含め非表示とする。
// - reaction: C10の表現。"single" | "multiple"。既定値は"multiple"(ctx=normal時のみ)。
//   複数リアクションはIssue #50のレビューで既決と確認したため既定値とする。
// - layout: 自分の確認のカラム数。"two-col" | "one-col"。既定値は"two-col"。
// - c18: Mobile側C18の固定方式。"inline" | "sticky"。既定値は"inline"。
//   ワイヤーフレームの「上部固定」案は、本文→価値→関連の情報順の決定(Issue #17)と
//   矛盾するため、本モックでは候補から外し「下部固定(sticky)」のみ比較対象とした。
// - c20target: C20対象Discoveryの表示形式。"link" | "summary"。既定値は"link"。
// - c20values: C20複数Valueの見せ方。"rows" | "cards"。既定値は"rows"。
// - c17multi: C17でC20が複数ある場合の見せ方。"stacked" | "summary"。既定値は"stacked"。
// - c15c20: 自分の確認でC15とC17が並ぶ場合の表現・配置。
//   "compact-after"(C15=リンクのみ・C17の後、価値を先に見せる。既定) |
//   "compact-before"(C15=リンクのみ・C17の前) | "card-before"(C15=Discovery Card・C17の前)。
//   既定値はPC-OwnCheck.png(採用Main)の並びに合わせた。
import Link from "next/link";
import { ContributionContent } from "@/components/C14-contribution-content/ContributionContent";
import { RelatedDiscoveryList } from "@/components/C15-related-discovery/RelatedDiscoveryList";
import { ContributionStatus } from "@/components/C16-contribution-status/ContributionStatus";
import { ContributionImpact } from "@/components/C17-contribution-impact/ContributionImpact";
import { ContributionConfirmation } from "@/components/C18-contribution-confirmation/ContributionConfirmation";
import { ReactionButton } from "@/components/C10-reaction-button/ReactionButton";
import { getContributionById } from "@/lib/mock-data/contribution";
import { cn } from "@/lib/utils";

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

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

const CTX_OPTIONS = ["normal", "own"] as const;
const OWNER_OPTIONS = ["self", "other"] as const;
const REACTION_OPTIONS = ["single", "multiple"] as const;
const LAYOUT_OPTIONS = ["two-col", "one-col"] as const;
const C18_OPTIONS = ["inline", "sticky"] as const;
const C20_TARGET_OPTIONS = ["link", "summary"] as const;
const C20_VALUES_OPTIONS = ["rows", "cards"] as const;
const C17_MULTI_OPTIONS = ["stacked", "summary"] as const;
const C15C20_OPTIONS = ["compact-after", "compact-before", "card-before"] as const;

const COMPARE_GROUPS: {
  key:
    | "ctx"
    | "owner"
    | "reaction"
    | "layout"
    | "c18"
    | "c20target"
    | "c20values"
    | "c17multi"
    | "c15c20";
  label: string;
  options: readonly string[];
}[] = [
  { key: "ctx", label: "閲覧コンテキスト", options: CTX_OPTIONS },
  { key: "owner", label: "本人か", options: OWNER_OPTIONS },
  { key: "reaction", label: "リアクション", options: REACTION_OPTIONS },
  { key: "layout", label: "自分の確認レイアウト", options: LAYOUT_OPTIONS },
  { key: "c18", label: "C18固定方式(Mobile)", options: C18_OPTIONS },
  { key: "c20target", label: "C20対象Discovery", options: C20_TARGET_OPTIONS },
  { key: "c20values", label: "C20複数Value", options: C20_VALUES_OPTIONS },
  { key: "c17multi", label: "C17複数時", options: C17_MULTI_OPTIONS },
  { key: "c15c20", label: "C15/C20配置", options: C15C20_OPTIONS },
];

export default async function ContributionDetailPage({ params, searchParams }: Props) {
  const { id } = await params;
  const sp = await searchParams;

  const ctx = pickParam(sp.ctx, CTX_OPTIONS, "normal");
  const owner = pickParam(sp.owner, OWNER_OPTIONS, "self");
  const reaction = pickParam(sp.reaction, REACTION_OPTIONS, "multiple");
  const layout = pickParam(sp.layout, LAYOUT_OPTIONS, "two-col");
  const c18 = pickParam(sp.c18, C18_OPTIONS, "inline");
  const c20target = pickParam(sp.c20target, C20_TARGET_OPTIONS, "link");
  const c20values = pickParam(sp.c20values, C20_VALUES_OPTIONS, "rows");
  const c17multi = pickParam(sp.c17multi, C17_MULTI_OPTIONS, "stacked");
  const c15c20 = pickParam(sp.c15c20, C15C20_OPTIONS, "compact-after");

  const current: Record<string, string> = {
    ctx,
    owner,
    reaction,
    layout,
    c18,
    c20target,
    c20values,
    c17multi,
    c15c20,
  };

  const compareHref = (key: string, value: string) => {
    const next = new URLSearchParams(current);
    next.set(key, value);
    return `?${next.toString()}`;
  };

  const contribution = getContributionById(id);
  const isOwnCheck = ctx === "own";
  const showOwnerOnly = isOwnCheck && owner === "self";
  const relatedDiscoveryVariant = c15c20 === "card-before" ? "card" : "link";

  const relatedDiscoverySection = (
    <RelatedDiscoveryList items={contribution.relatedDiscoveries} variant={relatedDiscoveryVariant} />
  );
  const impactSection = (
    <ContributionImpact
      discoveryImpacts={contribution.discoveryImpacts}
      multiVariant={c17multi}
      targetVariant={c20target}
      valuesVariant={c20values}
    />
  );

  return (
    <div className="flex flex-col gap-6 pb-16">
      <div className="flex flex-col gap-2 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        <span className="font-medium">[Mock比較用]</span>
        {COMPARE_GROUPS.map((group) => (
          <span key={group.key} className="flex flex-wrap items-center gap-x-1">
            <span className="shrink-0">{group.label}:</span>
            {group.options.map((opt) => (
              <Link
                key={opt}
                href={compareHref(group.key, opt)}
                className={
                  opt === current[group.key]
                    ? "mx-1 font-semibold text-foreground underline"
                    : "mx-1 hover:underline"
                }
              >
                {opt}
              </Link>
            ))}
          </span>
        ))}
      </div>

      <ContributionContent contribution={contribution} />

      {!isOwnCheck ? (
        <>
          <ReactionButton variant={reaction} initialCount={contribution.reactionCount} />
          {relatedDiscoverySection}
        </>
      ) : !showOwnerOnly ? (
        relatedDiscoverySection
      ) : (
        <div className={cn("flex flex-col gap-6", layout === "two-col" && "md:grid md:grid-cols-3")}>
          <div className={cn("flex flex-col gap-6", layout === "two-col" && "md:col-span-2")}>
            {c15c20 === "compact-after" ? (
              <>
                {impactSection}
                {relatedDiscoverySection}
              </>
            ) : (
              <>
                {relatedDiscoverySection}
                {impactSection}
              </>
            )}
          </div>
          <div className="flex flex-col gap-6">
            <ContributionConfirmation
              contributionId={id}
              confirmationRequests={contribution.confirmationRequests}
              className={c18 === "sticky" ? "hidden md:flex" : undefined}
            />
            <ContributionStatus contribution={contribution} />
          </div>
        </div>
      )}

      {showOwnerOnly && c18 === "sticky" ? (
        <div className="fixed inset-x-0 bottom-0 z-40 border-t bg-background px-4 py-3 md:hidden">
          <ContributionConfirmation
            contributionId={id}
            confirmationRequests={contribution.confirmationRequests}
            compact
          />
        </div>
      ) : null}
    </div>
  );
}
