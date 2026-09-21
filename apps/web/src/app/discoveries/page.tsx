// see docs/ui/screens/S02-discovery-list.md, docs/ui/wireframe/screens/S02/*.png
//
// モック用にsearchParamsで入口・状態・比較案を切り替える(切り替え方法自体は実装設計で決める)。
// - entry: "section"(S01「もっと見る」からの引き継ぎ。section=section-a等を併用) | "search"(C06指定。q=自然文) | "default"(条件なし)。
//   sectionまたはqが指定されていれば対応するentryとみなす。
// - state: "normal"(複数件) | "few"(少数件) | "expanded"(既定条件0件→範囲拡大) | "zero"(拡大しても0件) | "loading" | "error"
// - layout: "a" | "b" | "c"(C03/C04のレイアウト比較案。PC/Mobileで内容が異なる)
// 既定条件・既定半径・拡大範囲はダミーの仮置き値。採用判断はIssue #31で人間が行う。
import Link from "next/link";
import { Footer } from "@/components/C02-footer/Footer";
import { Header } from "@/components/C01-header/Header";
import { LoginDialog } from "@/components/C08-login-dialog/LoginDialog";
import { Loading } from "@/components/C09-loading/Loading";
import { DiscoveryList } from "@/components/C04-discovery-list/DiscoveryList";
import { ContributionCta } from "@/components/C21-contribution-cta/ContributionCta";
import { ScrollRestorer } from "@/components/layout/ScrollRestorer";
import { MockAuthProvider } from "@/lib/mock-auth";
import {
  DEFAULT_AREA, DEFAULT_RADIUS_KM, ENTRIES, EXPANDED_RADIUS_KM, FEW_COUNT_MAX, LAYOUTS, STATES,
  makeDiscoveries, sectionTitle, type Entry,
} from "@/lib/mock-data/discovery-list";

type Params = Record<string, string | string[] | undefined>;
const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);
function pick<T extends readonly string[]>(v: string | string[] | undefined, options: T, fallback: T[number]): T[number] {
  const x = first(v);
  return (options as readonly string[]).includes(x ?? "") ? (x as T[number]) : fallback;
}

export default async function DiscoveryListPage({ searchParams }: { searchParams: Promise<Params> }) {
  const params = await searchParams;
  const section = first(params.section);
  const q = first(params.q);
  const entry: Entry = pick(params.entry, ENTRIES, section ? "section" : q ? "search" : "default");
  const state = pick(params.state, STATES, "normal");
  const layout = pick(params.layout, LAYOUTS, "a");

  const hrefWith = (patch: Record<string, string>) => {
    const next = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) { const x = first(v); if (x !== undefined) next.set(k, x); }
    for (const [k, v] of Object.entries(patch)) next.set(k, v);
    return `/discoveries?${next.toString()}`;
  };
  const toggle = (label: string, key: string, value: string, current: string) => (
    <Link key={value} href={hrefWith({ [key]: value })} scroll={false} replace aria-current={current === value} className={current === value ? "font-semibold text-foreground underline" : "hover:underline"}>{label}</Link>
  );

  const items = state === "few" ? makeDiscoveries(2) : state === "expanded" ? makeDiscoveries(6) : state === "normal" ? makeDiscoveries(24) : [];
  const isEmpty = state === "zero";
  // C21: 0件=一覧の代わり(案B有力) / 少数件=一覧の最後のCard / 通常件数=表示しない。
  // 少数件の判定条件と文言は実装時に確定する(FEW_COUNT_MAX・文言は仮置き)。
  const isFew = items.length > 0 && items.length <= FEW_COUNT_MAX;
  const expanded = state === "expanded" || state === "zero";

  const chips: string[] =
    entry === "section" ? [`テーマ: ${sectionTitle(section) ?? "Section名 A"}`, "並び順: おすすめ順", `${items.length || 0}件`]
    : entry === "search" ? [q ? `自然文: ${q}` : "自然文: (未入力)", "現在地", `半径 ${DEFAULT_RADIUS_KM}km`]
    : [`現在地（取得不可の場合: ${DEFAULT_AREA}）`, `既定半径 ${DEFAULT_RADIUS_KM}km`];

  const list = <DiscoveryList items={items} layout={layout} extra={isFew ? <ContributionCta context="few" /> : undefined} />;

  let body;
  if (state === "loading") body = <Loading cardCount={8} />;
  else if (state === "error") body = <div role="alert" className="flex flex-col items-center gap-3 rounded-lg border px-4 py-12 text-center text-sm"><p>Discoveryを取得できませんでした。</p><Link href={hrefWith({ state: "normal" })} className="rounded-md border px-3 py-1.5 hover:bg-accent">再読み込み</Link></div>;
  else if (isEmpty) body = <ContributionCta context="empty" />;
  else body = list;

  return <MockAuthProvider><Header />
    <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:py-8">
      <ScrollRestorer />
      <div className="mb-6 flex flex-col gap-1.5 rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        <div className="flex flex-wrap items-center gap-3"><span className="font-medium text-foreground">[Mock比較用]</span><span>入口:</span>
          <Link href="/discoveries?entry=section&section=section-a" className={entry === "section" ? "font-semibold text-foreground underline" : "hover:underline"}>S01もっと見る</Link>
          <Link href="/discoveries?entry=search&q=静かな城跡" className={entry === "search" ? "font-semibold text-foreground underline" : "hover:underline"}>C06指定</Link>
          <Link href="/discoveries" className={entry === "default" ? "font-semibold text-foreground underline" : "hover:underline"}>条件なし</Link></div>
        <div className="flex flex-wrap items-center gap-3"><span>状態:</span>
          {toggle("複数件", "state", "normal", state)}{toggle("少数件", "state", "few", state)}{toggle("既定0件→範囲拡大", "state", "expanded", state)}{toggle("拡大しても0件", "state", "zero", state)}{toggle("取得中", "state", "loading", state)}{toggle("取得失敗", "state", "error", state)}</div>
        <div className="flex flex-wrap items-center gap-3"><span>C03/C04レイアウト:</span>
          {toggle("A: PC4列/Mobile縦1列", "layout", "a", layout)}{toggle("B: PC3列/Mobile横型", "layout", "b", layout)}{toggle("C: PC横型/Mobile 2列", "layout", "c", layout)}</div>
        <p>既定条件・半径・拡大範囲は仮置き。C21: 0件=一覧の代わり(案B有力)/少数件=最後のCard/通常件数=非表示。少数件の判定条件と文言は実装時に確定。</p>
      </div>

      <h1 className="mb-3 text-xl font-bold">Discovery探索</h1>
      <ul aria-label="探索条件" className="mb-4 flex flex-wrap gap-2 text-xs">{chips.map((c) => <li key={c} className="rounded-full border px-3 py-1">{c}</li>)}</ul>
      {expanded ? <p data-testid="expanded-range-notice" role="status" className="mb-4 rounded-md border border-primary/40 bg-primary/5 px-3 py-2 text-sm">{state === "expanded" ? `既定の範囲(${DEFAULT_RADIUS_KM}km)では見つからなかったため、範囲を${EXPANDED_RADIUS_KM}kmに広げて表示しています。` : `範囲を${EXPANDED_RADIUS_KM}kmに広げて表示しています。`}</p> : null}
      {body}
    </main>
    <Footer /><LoginDialog />
  </MockAuthProvider>;
}
