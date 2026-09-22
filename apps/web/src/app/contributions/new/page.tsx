// see docs/ui/screens/S04-contribution-save.md, docs/ai/claude/wireframe/S04-wireframe-source.html
//
// モック用の仮ルート。Wireframeで確認した状態差はsearchParamsで直接切り替えられる
// ようにする(S03/S05モックと同様の方式)。多くの状態(投稿種別・Disclosure開閉・
// 資料の追加/削除等)はフォーム上で直接操作でき、この比較バーは確認の入口を
// 揃えるための補助(Issue #43)。
import Link from "next/link";
import { ContributionEditor } from "@/components/layout/ContributionEditor";
import { createEmptyDraft, ORIGIN_DISCOVERY_PRESETS, type DraftMedia } from "@/lib/mock-data/contribution-draft";

type Props = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

function pickParam<T extends readonly string[]>(
  value: string | string[] | undefined,
  options: T,
  fallback: T[number],
): T[number] {
  const v = Array.isArray(value) ? value[0] : value;
  return (options as readonly string[]).includes(v ?? "") ? (v as T[number]) : fallback;
}

const TYPE_OPTIONS = ["knowledge", "question"] as const;
const DISCLOSURE_OPTIONS = ["closed", "open"] as const;
const MEDIA_OPTIONS = ["without", "with"] as const;
const SOURCE_OPTIONS = ["unset", "self", "registered", "unknown"] as const;
const AUTH_OPTIONS = ["user", "guest"] as const;
const FROM_OPTIONS = ["none", "sample-1"] as const;

const COMPARE_GROUPS: { key: string; label: string; options: readonly string[] }[] = [
  { key: "from", label: "遷移元Discovery", options: FROM_OPTIONS },
  { key: "type", label: "投稿種別", options: TYPE_OPTIONS },
  { key: "disclosure", label: "任意詳細の開閉", options: DISCLOSURE_OPTIONS },
  { key: "media", label: "資料の有無", options: MEDIA_OPTIONS },
  { key: "source", label: "資料の出典状態", options: SOURCE_OPTIONS },
  { key: "auth", label: "ログイン状態", options: AUTH_OPTIONS },
];

export default async function ContributionNewPage({ searchParams }: Props) {
  const sp = await searchParams;
  const from = pickParam(sp.from, FROM_OPTIONS, "none");
  const type = pickParam(sp.type, TYPE_OPTIONS, "knowledge");
  const disclosure = pickParam(sp.disclosure, DISCLOSURE_OPTIONS, "closed");
  const media = pickParam(sp.media, MEDIA_OPTIONS, "without");
  const source = pickParam(sp.source, SOURCE_OPTIONS, "unset");
  const auth = pickParam(sp.auth, AUTH_OPTIONS, "guest");
  const resume = Array.isArray(sp.resume) ? sp.resume[0] : sp.resume;

  const current: Record<string, string> = { from, type, disclosure, media, source, auth };
  const compareHref = (key: string, value: string) => {
    const next = new URLSearchParams(current);
    next.set(key, value);
    return `?${next.toString()}`;
  };

  const draft = createEmptyDraft(from === "none" ? undefined : from);
  draft.contributionType = type;
  if (media === "with") {
    const seeded: DraftMedia = {
      id: "seed-1",
      name: "写真1.jpg",
      kind: "image",
      sourceStatus: source,
      flaggedInappropriate: false,
    };
    draft.media = [seeded];
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        <span className="font-medium">[Mock確認用]</span>
        {COMPARE_GROUPS.map((group) => (
          <span key={group.key} className="flex flex-wrap items-center gap-x-1">
            <span className="shrink-0">{group.label}:</span>
            {group.options.map((opt) => (
              <Link
                key={opt}
                href={compareHref(group.key, opt)}
                className={
                  opt === current[group.key] ? "mx-1 font-semibold text-foreground underline" : "mx-1 hover:underline"
                }
              >
                {opt}
              </Link>
            ))}
          </span>
        ))}
        {from !== "none" ? (
          <span className="text-muted-foreground">遷移元: {ORIGIN_DISCOVERY_PRESETS[from]?.title}</span>
        ) : null}
      </div>
      <ContributionEditor
        initialDraft={draft}
        initialDisclosureOpen={disclosure === "open"}
        initialAuth={auth}
        resumeHandoffId={resume}
      />
    </div>
  );
}
