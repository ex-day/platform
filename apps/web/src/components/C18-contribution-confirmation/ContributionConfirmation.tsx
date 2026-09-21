// see docs/ui/components/C18-contribution-confirmation.md
//
// 確認・補完するはS04(編集)へ入口のみ遷移する(S04未実装のため、
// S04→S11の実際の画面遷移はモックでは確認できない。Issue #22参照)。
import Link from "next/link";
import { TriangleAlertIcon } from "lucide-react";
import { buttonVariants } from "@/components/primitives/button";
import { cn } from "@/lib/utils";

export function ContributionConfirmation({
  contributionId,
  confirmationRequests,
  className,
  compact = false,
}: {
  contributionId: string;
  confirmationRequests: string[];
  className?: string;
  /**
   * Mobile: C18上部/下部固定の比較案(Non-blocking #8)のうち、下部固定案用の
   * 簡易表示。件数のみを示し、詳細な確認事項の列挙は省く。
   */
  compact?: boolean;
}) {
  if (confirmationRequests.length === 0) return null;

  if (compact) {
    return (
      <div className={cn("flex items-center justify-between gap-3 text-sm", className)}>
        <span className="flex items-center gap-1.5">
          <TriangleAlertIcon className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
          確認が必要な内容があります({confirmationRequests.length}件)
        </span>
        <Link
          href={`/contributions/${contributionId}/edit`}
          className={buttonVariants({ size: "sm", className: "shrink-0" })}
        >
          確認・補完する
        </Link>
      </div>
    );
  }

  return (
    <section className={cn("flex flex-col gap-3 rounded-lg border bg-muted/40 p-4", className)}>
      <h2 className="text-base font-semibold">確認が必要な内容</h2>
      <ul className="flex flex-col gap-1.5 text-sm">
        {confirmationRequests.map((request) => (
          <li key={request} className="flex items-start gap-1.5">
            <TriangleAlertIcon className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
            {request}
          </li>
        ))}
      </ul>
      <Link
        href={`/contributions/${contributionId}/edit`}
        className={buttonVariants({ className: "w-fit" })}
      >
        確認・補完する
      </Link>
    </section>
  );
}
