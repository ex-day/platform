// see docs/ui/components/C16-contribution-status.md
import Link from "next/link";
import { buttonVariants } from "@/components/primitives/button";
import type { Contribution } from "@/lib/mock-data/contribution";

export function ContributionStatus({ contribution }: { contribution: Contribution }) {
  return (
    <section className="flex flex-col gap-3 rounded-lg border p-4">
      <h2 className="text-base font-semibold">現在の状態</h2>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
        <dt className="text-muted-foreground">状態</dt>
        <dd className="font-medium">{contribution.status.label}</dd>
        {contribution.status.confirmedContext ? (
          <>
            <dt className="text-muted-foreground">対象場所・時期</dt>
            <dd>{contribution.status.confirmedContext}</dd>
          </>
        ) : null}
        {contribution.status.subject ? (
          <>
            <dt className="text-muted-foreground">内容分類</dt>
            <dd>{contribution.status.subject}</dd>
          </>
        ) : null}
      </dl>
      <Link
        href={`/contributions/${contribution.id}/edit`}
        className={buttonVariants({ variant: "outline", size: "sm", className: "w-fit" })}
      >
        内容を編集する
      </Link>
    </section>
  );
}
