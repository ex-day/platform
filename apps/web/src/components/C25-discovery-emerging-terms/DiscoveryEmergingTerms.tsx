// see docs/ui/components/C25-discovery-emerging-terms.md, DEC-0009 決定4・9
//
// わかってきたこと(Finding)を価値と説に分けて表示し、種類ごとのリアクションを扱う。
// - 価値:「わかる」「行ってみたい」
// - 裏付けのない説:「そうかも」(正しさの証拠にはしない。票の多さで並べ替えない)
// - 裏付けのある説: リアクションを付けず、裏付け(出典)を示す
// 見せる基準・表示上限・押下時の挙動はC25の検討事項のため、モックでは受け取った順に表示する。
"use client";

import { useState } from "react";
import { BookOpenIcon } from "lucide-react";
import { useMockAuth } from "@/lib/mock-auth";
import { cn } from "@/lib/utils";
import type { DiscoveryFinding } from "@/lib/mock-data/discovery";

type ReactionKey = "understand" | "wantToGo" | "maybe";

const REACTION_LABELS: Record<ReactionKey, string> = {
  understand: "わかる",
  wantToGo: "行ってみたい",
  maybe: "そうかも",
};

function FindingReaction({ reactionKey, initialCount }: { reactionKey: ReactionKey; initialCount: number }) {
  const { user, openLogin } = useMockAuth();
  const [active, setActive] = useState(false);
  const count = initialCount + (active ? 1 : 0);
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={() => (user ? setActive((v) => !v) : openLogin())}
      className={cn(
        "rounded-full border px-2 py-0.5 text-xs transition-colors",
        active ? "border-primary bg-primary/10 text-primary" : "border-input hover:bg-accent",
      )}
    >
      {REACTION_LABELS[reactionKey]}
      <span className="ml-1 text-muted-foreground">{count}</span>
    </button>
  );
}

function FindingItem({ finding }: { finding: DiscoveryFinding }) {
  const keys: ReactionKey[] =
    finding.kind === "value" ? ["understand", "wantToGo"] : finding.backedBy ? [] : ["maybe"];
  return (
    <li className="flex flex-col gap-1.5 rounded-md border border-dashed px-3 py-2">
      <p className="text-sm">{finding.text}</p>
      <div className="flex flex-wrap items-center gap-1.5">
        {finding.backedBy ? (
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <BookOpenIcon className="h-3.5 w-3.5" aria-hidden />
            裏付け：{finding.backedBy}
          </span>
        ) : null}
        {keys.map((key) => (
          <FindingReaction key={key} reactionKey={key} initialCount={finding.reactions[key] ?? 0} />
        ))}
      </div>
    </li>
  );
}

export function DiscoveryEmergingTerms({ findings }: { findings: DiscoveryFinding[] }) {
  if (findings.length === 0) return null;
  const values = findings.filter((f) => f.kind === "value");
  const theories = findings.filter((f) => f.kind === "theory");

  return (
    <section className="flex flex-col gap-3" aria-labelledby="findings-heading">
      <h2 id="findings-heading" className="text-sm font-semibold">
        わかってきたこと
        <span className="ml-1 text-xs font-normal text-muted-foreground">（みんなの声から見えてきたこと）</span>
      </h2>
      {values.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          <h3 className="text-xs font-medium text-muted-foreground">価値</h3>
          <ul className="grid gap-2 sm:grid-cols-2">
            {values.map((f) => <FindingItem key={f.id} finding={f} />)}
          </ul>
        </div>
      ) : null}
      {theories.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          <h3 className="text-xs font-medium text-muted-foreground">説</h3>
          <ul className="grid gap-2 sm:grid-cols-2">
            {theories.map((f) => <FindingItem key={f.id} finding={f} />)}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
