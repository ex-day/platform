// see docs/ui/components/C11-discovery-sections.md, docs/ui/screens/S01-top.md, docs/api/openapi.yaml(getTop)
//
// API のデータの C11(Issue #113、#135)。モックのデータの C11 は ./MockDiscoverySections.tsx(/mock)。
// - ブラウザ側で、同じオリジンの中継(@/lib/api/browser-client)を通して、GET /top を1回だけ取る。
// - API は DB V2 の形(v0.2。#135)にした。画面はまだ作り直しておらず、今のコンポーネントに当てはめる最小の対応にしている。
//   今は Discovery のカード(C03)で出せる枠(お知らせ・いま・この時期・近くの発見・見本)だけを出す。
//   企画・番付・探しています・声を待っている、小さな地図・このあたりの数字は、S01 の画面を作り直すときに足す。
// - 枠の順は API が決めたまま(画面で並べ替えない)。PC は縦に並べ、モバイルはタブにする。
// - PC とモバイルの判断は、ここの画面幅(PC_MEDIA_QUERY)の1か所だけで行う。
// - 戻ったときに見失わないようにする(DEC-0022 決定4、C11 の「S03 から戻ったときに選択タブとタブ内の位置を復元する」):
//   取得した TOP の応答・選択中のタブ・スクロール位置を、このタブのセッションストレージに持つ。ブラウザの戻る・進む
//   (popstate、またはページの読み込みの種類が back_forward)で TOP に来たときは、取り直さずにそれを戻す。
//   それ以外(リンクで開く、再読み込み)は取り直して上書きする。持つのはこのタブのセッションの間だけ(期限は設けない)。
"use client";

import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { DiscoverySection } from "@/components/C05-discovery-section/DiscoverySection";
import { Loading } from "@/components/C09-loading/Loading";
import { browserApiClient, type TopPage, type TopSection } from "@/lib/api/browser-client";
import { EXAMPLE_LABEL, toTopDiscoveryCard } from "@/lib/api/discovery-view";
import { DISCOVERY_HREF_BASE } from "@/lib/discovery-href";
import type { RelatedDiscoverySummary } from "@/lib/mock-data/discovery";

/** Tailwind の md(768px)。C04 の列数・モックの C11 の切り替えと同じ境目 */
const PC_MEDIA_QUERY = "(min-width: 768px)";

type Layout = "pc" | "mobile";
type SectionKey = TopSection["kind"];
type SavedTop = { page: TopPage; activeKey?: SectionKey; scrollY: number };

/** セッションストレージのキー。TOP の URL(起点の指定を含む)ごとに持つ */
const STORAGE_PREFIX = "exday:top:";

// 戻る・進むで来たか。popstate は Next.js が TOP を描き直すより先に届くので、ここで覚えておく
let cameByHistoryTraversal = false;
if (typeof window !== "undefined") {
  window.addEventListener("popstate", () => {
    cameByHistoryTraversal = true;
  });
  // 別の文書から戻ってきた(ページごと読み直された)とき
  const navigation = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
  cameByHistoryTraversal = navigation?.type === "back_forward";
}

/** 戻る・進むで来たなら、持っておいた TOP を返す(サーバー側では何もしない) */
function restoreOnTraversal(): SavedTop | undefined {
  if (typeof window === "undefined" || !cameByHistoryTraversal) return undefined;
  return loadSaved();
}

function storageKey(): string {
  return STORAGE_PREFIX + window.location.pathname + window.location.search;
}

function loadSaved(): SavedTop | undefined {
  try {
    const raw = window.sessionStorage.getItem(storageKey());
    return raw ? (JSON.parse(raw) as SavedTop) : undefined;
  } catch {
    return undefined;
  }
}

function save(saved: SavedTop) {
  try {
    window.sessionStorage.setItem(storageKey(), JSON.stringify(saved));
  } catch {
    // 保存できない(容量・プライベートブラウズ等)ときは、戻ったときに取り直すだけ
  }
}
type ShownSection = { key: SectionKey; title: string; items: RelatedDiscoverySummary[] };

function subscribeLayout(onChange: () => void) {
  const mql = window.matchMedia(PC_MEDIA_QUERY);
  mql.addEventListener("change", onChange);
  return () => mql.removeEventListener("change", onChange);
}

/** サーバー側と、ブラウザで画面幅が分かるまでは undefined */
function useLayout(): Layout | undefined {
  return useSyncExternalStore(
    subscribeLayout,
    () => (window.matchMedia(PC_MEDIA_QUERY).matches ? "pc" : "mobile"),
    () => undefined,
  );
}

async function fetchTop(): Promise<TopPage | "error"> {
  try {
    const { data } = await browserApiClient().GET("/top");
    return data ?? "error";
  } catch {
    return "error";
  }
}

/** Discovery のカードで出せる枠だけを、API の順のまま、カードの props にする */
function toShownSections(page: TopPage): ShownSection[] {
  const shown: ShownSection[] = [];
  for (const section of page.sections) {
    switch (section.kind) {
      case "outside_start_area":
        shown.push({ key: section.kind, title: "このあたりはまだ発見が少ない", items: [toTopDiscoveryCard(section.nearestDiscovery)] });
        break;
      case "seasonal":
        shown.push({
          key: section.kind,
          title: "いま・この時期",
          items: section.items.map((item) => toTopDiscoveryCard(item.card, item.value.description)),
        });
        break;
      case "nearby_discoveries":
        shown.push({ key: section.kind, title: "近くの発見", items: section.items.map((card) => toTopDiscoveryCard(card)) });
        break;
      case "examples":
        shown.push({ key: section.kind, title: EXAMPLE_LABEL, items: section.items.map((card) => toTopDiscoveryCard(card)) });
        break;
      default:
        // 企画・番付・探しています・声を待っているは、S01 の画面を作り直すときに足す
        break;
    }
  }
  return shown;
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

function SectionBody({ section }: { section: ShownSection }) {
  return (
    <DiscoverySection
      section={{ id: section.key, title: section.title, items: section.items }}
      hideTitle
      hrefBase={DISCOVERY_HREF_BASE}
    />
  );
}

export function DiscoverySections() {
  const layout = useLayout();
  // 戻る・進むで来たときに戻すもの(最初の描画で1回だけ読む)
  const [restored] = useState(restoreOnTraversal);
  const [page, setPage] = useState<TopPage | "error" | undefined>(restored?.page);
  const [activeKey, setActiveKey] = useState<SectionKey | undefined>(restored?.activeKey);
  // 取りに行ったか。開発時の StrictMode で effect が2回動いても、2回取らないようにする
  const requested = useRef(false);
  // 戻ったときに戻すスクロール位置(描いたあとに1回だけ使う)
  const pendingScrollY = useRef<number | undefined>(restored?.scrollY);
  const scrollY = useRef(restored?.scrollY ?? 0);
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

  function load() {
    void fetchTop().then((result) => {
      setPage(result);
      if (result !== "error") save({ page: result, scrollY: 0 });
    });
  }

  useEffect(() => {
    if (requested.current) return;
    requested.current = true;
    // 次に TOP へ来たときの判断のために戻す
    cameByHistoryTraversal = false;
    if (restored) return;
    void fetchTop().then((result) => {
      setPage(result);
      if (result !== "error") save({ page: result, scrollY: 0 });
    });
  }, [restored]);

  // 描き終えたら、戻したスクロール位置へ動かす
  useLayoutEffect(() => {
    if (!layout || !page || page === "error" || pendingScrollY.current === undefined) return;
    const y = pendingScrollY.current;
    pendingScrollY.current = undefined;
    window.scrollTo(0, y);
  }, [layout, page]);

  // スクロール位置と選択中のタブを、取得済みの応答と一緒に持つ
  useEffect(() => {
    if (!page || page === "error") return;
    let frame = 0;
    const persist = () => save({ page, activeKey, scrollY: scrollY.current });
    const onScroll = () => {
      if (pendingScrollY.current !== undefined) return;
      scrollY.current = window.scrollY;
      if (frame) return;
      frame = window.requestAnimationFrame(() => {
        frame = 0;
        persist();
      });
    };
    persist();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, [page, activeKey]);

  function retry() {
    setPage(undefined);
    load();
  }

  if (page === "error") return <Notice onRetry={retry}>TOP の発見を読み込めませんでした。</Notice>;
  if (!layout || !page) return <Loading cardCount={layout === "mobile" ? 1 : undefined} />;
  const sections = toShownSections(page);
  if (sections.length === 0) return <Notice>このあたりには、まだ発見がありません。</Notice>;

  if (layout === "pc") {
    return (
      <div className="flex flex-col gap-10">
        {sections.map((section) => (
          <section key={section.key} aria-labelledby={`${section.key}-title`} className="flex flex-col gap-4">
            <h2 id={`${section.key}-title`} className="text-xl font-bold">{section.title}</h2>
            <SectionBody section={section} />
          </section>
        ))}
      </div>
    );
  }

  const activeSection = sections.find((section) => section.key === activeKey) ?? sections[0];

  function selectAdjacentTab(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const count = sections.length;
    let nextIndex: number | undefined;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % count;
    if (event.key === "ArrowLeft") nextIndex = (index - 1 + count) % count;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = count - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    setActiveKey(sections[nextIndex].key);
    tabRefs.current[nextIndex]?.focus();
  }

  return (
    <div>
      <div role="tablist" aria-label="TOP の枠" className="mb-5 flex gap-6 overflow-x-auto border-b">
        {sections.map((section, index) => {
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
        <SectionBody section={activeSection} />
      </div>
    </div>
  );
}
