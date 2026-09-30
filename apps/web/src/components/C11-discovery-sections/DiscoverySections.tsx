// see docs/ui/components/C11-discovery-sections.md, docs/api/openapi.yaml(getDiscoverySections・getDiscoverySectionItems)
//
// API のデータの C11(Issue #113)。モックのデータの C11 は ./MockDiscoverySections.tsx(/mock)。
// 描画方式は #89 のコメント「S01 の描画方式（変更：ブラウザ側で取る）」による。
// - ブラウザ側で、同じオリジンの中継(@/lib/api/browser-client)を通して、セクションの一覧と中身を取る。
// - PC とモバイルの判断は、ここの画面幅(PC_MEDIA_QUERY)の1か所だけで行う。
//   - PC(縦に並べる表示)：全セクションを取る。セクションごとに読み込み中(C09)を出し、取れたものから表示する。
//   - モバイル(タブ表示)：開いているタブのセクションだけを取る。別のタブを開いたときに、そのセクションを取る。
// - 一度取ったセクションは、この画面を開いている間は持っておき、タブを戻しても取り直さない。
//   画面幅が変わった(回転など)ときは、持っているものはそのまま使い、足りないセクションだけを取る。
// - 1つのセクションの取得に失敗しても、ほかのセクションは表示する。失敗したセクションには、
//   その旨と「再読み込み」を出す。
"use client";

import { useEffect, useRef, useState, useSyncExternalStore, type KeyboardEvent, type ReactNode } from "react";
import { DiscoverySection } from "@/components/C05-discovery-section/DiscoverySection";
import { Loading } from "@/components/C09-loading/Loading";
import { browserApiClient, type DiscoverySectionList, type SectionKey } from "@/lib/api/browser-client";
import { toRelatedSummary } from "@/lib/api/discovery-view";
import { DISCOVERY_HREF_BASE } from "@/lib/discovery-href";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";

/** Tailwind の md(768px)。C04 の列数・モックの C11 の切り替えと同じ境目 */
const PC_MEDIA_QUERY = "(min-width: 768px)";

type Layout = "pc" | "mobile";
type Section = DiscoverySectionList["sections"][number];
type SectionResult = { status: "done"; items: RelatedDiscoverySummary[] } | { status: "error" };

function subscribeLayout(onChange: () => void) {
  const mql = window.matchMedia(PC_MEDIA_QUERY);
  mql.addEventListener("change", onChange);
  return () => mql.removeEventListener("change", onChange);
}

/** サーバー側と、ブラウザで画面幅が分かるまでは undefined(どちらのセクションも取らない) */
function useLayout(): Layout | undefined {
  return useSyncExternalStore(
    subscribeLayout,
    () => (window.matchMedia(PC_MEDIA_QUERY).matches ? "pc" : "mobile"),
    () => undefined,
  );
}

async function fetchSections(): Promise<Section[] | "error"> {
  try {
    const { data } = await browserApiClient().GET("/discovery-sections");
    return data?.sections ?? "error";
  } catch {
    return "error";
  }
}

async function fetchSectionItems(key: SectionKey): Promise<SectionResult> {
  try {
    const { data } = await browserApiClient().GET("/discovery-sections/{sectionKey}/discoveries", {
      params: { path: { sectionKey: key } },
    });
    return data ? { status: "done", items: data.items.map(toRelatedSummary) } : { status: "error" };
  } catch {
    return { status: "error" };
  }
}

function Notice({ children, onRetry }: { children: ReactNode; onRetry?: () => void }) {
  return (
    <div role={onRetry ? "alert" : undefined} className="flex flex-wrap items-center gap-3 rounded-lg border border-dashed px-4 py-6 text-sm text-muted-foreground">
      <span>{children}</span>
      {onRetry ? (
        <button type="button" onClick={onRetry} className="font-medium text-foreground underline underline-offset-4 hover:text-muted-foreground">
          再読み込み
        </button>
      ) : null}
    </div>
  );
}

function SectionBody({ section, result, hideTitle, loadingCardCount, onRetry }: {
  section: Section;
  result: SectionResult | undefined;
  hideTitle: boolean;
  loadingCardCount?: number;
  onRetry: () => void;
}) {
  if (!result) return <Loading cardCount={loadingCardCount} />;
  if (result.status === "error") return <Notice onRetry={onRetry}>このセクションを読み込めませんでした。</Notice>;
  if (result.items.length === 0) return <Notice>今は提案できるDiscoveryがありません。</Notice>;
  return (
    <DiscoverySection
      section={{ id: section.key, title: section.title, items: result.items }}
      hideTitle={hideTitle}
      hrefBase={DISCOVERY_HREF_BASE}
    />
  );
}

export function DiscoverySections() {
  const layout = useLayout();
  const [sections, setSections] = useState<Section[] | "error">();
  const [results, setResults] = useState<Partial<Record<SectionKey, SectionResult>>>({});
  const [activeKey, setActiveKey] = useState<SectionKey>();
  // 取りに行ったもの。開発時の StrictMode で effect が2回動いても、同じものを2回取らないようにする
  const sectionsRequested = useRef(false);
  const itemsRequested = useRef(new Set<SectionKey>());
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

  useEffect(() => {
    if (sectionsRequested.current) return;
    sectionsRequested.current = true;
    void fetchSections().then(setSections);
  }, []);

  const loadedSections = Array.isArray(sections) ? sections : undefined;
  const currentKey = activeKey ?? loadedSections?.[0]?.key;

  useEffect(() => {
    if (!layout || !loadedSections) return;
    const keys = layout === "pc" ? loadedSections.map((s) => s.key) : currentKey ? [currentKey] : [];
    for (const key of keys) {
      if (itemsRequested.current.has(key)) continue;
      itemsRequested.current.add(key);
      void fetchSectionItems(key).then((result) => setResults((prev) => ({ ...prev, [key]: result })));
    }
  }, [layout, loadedSections, currentKey]);

  function retrySections() {
    setSections(undefined);
    void fetchSections().then(setSections);
  }

  function retrySection(key: SectionKey) {
    setResults((prev) => ({ ...prev, [key]: undefined }));
    void fetchSectionItems(key).then((result) => setResults((prev) => ({ ...prev, [key]: result })));
  }

  if (sections === "error") return <Notice onRetry={retrySections}>Discoveryの提案を読み込めませんでした。</Notice>;
  if (!layout || !loadedSections) return <Loading cardCount={layout === "mobile" ? 1 : undefined} />;
  if (loadedSections.length === 0) return <Notice>今は提案できるDiscoveryがありません。</Notice>;

  if (layout === "pc") {
    return (
      <div className="flex flex-col gap-10">
        {loadedSections.map((section) => (
          <section key={section.key} aria-labelledby={`${section.key}-title`} className="flex flex-col gap-4">
            <h2 id={`${section.key}-title`} className="text-xl font-bold">{section.title}</h2>
            <SectionBody section={section} result={results[section.key]} hideTitle onRetry={() => retrySection(section.key)} />
          </section>
        ))}
      </div>
    );
  }

  const activeSection = loadedSections.find((section) => section.key === currentKey) ?? loadedSections[0];

  function selectAdjacentTab(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const count = loadedSections!.length;
    let nextIndex: number | undefined;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % count;
    if (event.key === "ArrowLeft") nextIndex = (index - 1 + count) % count;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = count - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    setActiveKey(loadedSections![nextIndex].key);
    tabRefs.current[nextIndex]?.focus();
  }

  return (
    <div>
      <div role="tablist" aria-label="Discoveryセクション" className="mb-5 flex gap-6 overflow-x-auto border-b">
        {loadedSections.map((section, index) => {
          const selected = section.key === activeSection.key;
          return (
            <button
              key={section.key}
              ref={(node) => { tabRefs.current[index] = node; }}
              id={`${section.key}-tab`}
              type="button"
              role="tab"
              data-state={selected ? "active" : "inactive"}
              aria-selected={selected}
              aria-controls="discovery-section-panel"
              tabIndex={selected ? 0 : -1}
              onClick={() => setActiveKey(section.key)}
              onKeyDown={(event) => selectAdjacentTab(event, index)}
              className={`shrink-0 rounded-t-md border-b-4 px-3 py-2 text-sm transition-colors ${selected ? "border-foreground bg-muted font-bold text-foreground" : "border-transparent text-muted-foreground hover:bg-muted/60 hover:text-foreground"}`}
            >
              {section.title}
            </button>
          );
        })}
      </div>
      <div id="discovery-section-panel" role="tabpanel" aria-labelledby={`${activeSection.key}-tab`} tabIndex={0}>
        <SectionBody
          section={activeSection}
          result={results[activeSection.key]}
          hideTitle
          loadingCardCount={1}
          onRetry={() => retrySection(activeSection.key)}
        />
      </div>
    </div>
  );
}
