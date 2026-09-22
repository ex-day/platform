// see docs/ui/components/C21-contribution-cta.md
// C03(Discovery Card)を再利用せず、Discoveryとして数えない。
// 0件時(panel: 一覧の代わり。Empty Stateを兼ねる)と少数件時(card: 一覧の最後のCard)で文言を出し分ける。
// 文言はいずれも仮置き(最終文言は実装時に確定)。少数件用は「見つかりません」系を使わない。
import Link from "next/link";
import { PencilLineIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export const CONTRIBUTION_CTA_HREF = "/contributions/new";

// 仮文言(最終文言は実装時に確定)
const MESSAGES = {
  empty: "周辺に今のおすすめが見つかりません。周辺情報を投稿してみませんか？",
  few: "まだ知られていない発見があるかもしれません。あなたの知識や疑問を投稿してみませんか？",
} as const;

export function ContributionCta({ context, className }: { context: keyof typeof MESSAGES; className?: string }) {
  const panel = context === "empty";
  return <aside data-testid="c21-cta" data-shape={panel ? "panel" : "card"} className={cn("flex flex-col gap-4 rounded-lg border border-dashed bg-muted/40", panel ? "items-center px-4 py-12 text-center" : "items-start justify-center p-4", className)}>
    <p className="text-sm font-semibold">{MESSAGES[context]}</p>
    <Link href={CONTRIBUTION_CTA_HREF} className="inline-flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"><PencilLineIcon className="h-4 w-4" aria-hidden />知識・疑問を投稿する</Link>
  </aside>;
}
