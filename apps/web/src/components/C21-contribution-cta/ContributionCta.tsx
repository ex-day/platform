// see docs/ui/components/C21-contribution-cta.md
// C03(Discovery Card)を再利用せず、Discoveryとして数えない。文言・表示形式は仮置き。
// 表示場所はS02側で案A(一覧内カード)/案B(一覧の代わり)/案C(一覧上部)から切り替える。
import Link from "next/link";
import { PencilLineIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export const CONTRIBUTION_CTA_HREF = "/contributions/new"; // S04は未実装。入口のみの暫定

export function ContributionCta({ shape, className }: { shape: "card" | "panel" | "banner"; className?: string }) {
  const action = <Link href={CONTRIBUTION_CTA_HREF} className="inline-flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"><PencilLineIcon className="h-4 w-4" aria-hidden />知識・疑問を投稿する</Link>;
  const message = <p className="text-sm font-semibold">周辺に今のおすすめが見つかりません。周辺情報を投稿してみませんか？</p>;
  if (shape === "banner") return <aside data-testid="c21-cta" data-shape="banner" className={cn("flex flex-col gap-3 rounded-lg border border-dashed bg-muted/40 p-4 sm:flex-row sm:items-center sm:justify-between", className)}>{message}{action}</aside>;
  if (shape === "panel") return <aside data-testid="c21-cta" data-shape="panel" className={cn("flex flex-col items-center gap-4 rounded-lg border border-dashed bg-muted/40 px-4 py-12 text-center", className)}>{message}{action}</aside>;
  return <aside data-testid="c21-cta" data-shape="card" className={cn("flex flex-col items-start justify-center gap-4 rounded-lg border border-dashed bg-muted/40 p-4", className)}>{message}{action}</aside>;
}
