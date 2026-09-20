// see docs/ui/components/C10-reaction-button.md
//
// リアクションの種類・表現方法、単一/複数の可否は検討事項のため、
// Issue #8ではvariantで両案をトグル実装し、比較できる状態にする。
// 採用案の決定はIssue #8で人間が行う。
// 複数リアクション案の絵文字(👍/😮/♥)はdocs/ui/wireframe/S03/PC-ComponentVariants.png
// の案2に合わせた。
"use client";

import { useState } from "react";
import { HeartIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { useMockAuth } from "@/lib/mock-auth";

type ReactionVariant = "single" | "multiple";

const REACTION_TYPES = [
  { key: "like", emoji: "👍" },
  { key: "surprised", emoji: "😮" },
  { key: "love", emoji: "♥" },
] as const;

function useReactionState(initialCount: number) {
  const [active, setActive] = useState(false);
  const [count, setCount] = useState(initialCount);

  const toggle = () => {
    setActive((prev) => {
      const next = !prev;
      setCount((c) => c + (next ? 1 : -1));
      return next;
    });
  };

  return { active, count, toggle };
}

function ReactionChip({
  emoji,
  isLoggedIn,
  onRequireLogin,
}: {
  emoji: string;
  isLoggedIn: boolean;
  onRequireLogin: () => void;
}) {
  const { active, count, toggle } = useReactionState(0);

  return (
    <button
      type="button"
      onClick={() => (isLoggedIn ? toggle() : onRequireLogin())}
      aria-pressed={active}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm transition-colors",
        active
          ? "border-primary bg-primary/10 text-primary"
          : "border-input text-foreground hover:bg-accent",
      )}
    >
      <span aria-hidden>{emoji}</span>
      <span>{count}</span>
    </button>
  );
}

export function ReactionButton({
  variant,
  initialCount,
}: {
  variant: ReactionVariant;
  initialCount: number;
}) {
  const { user, openLogin } = useMockAuth();
  const single = useReactionState(initialCount);

  if (variant === "multiple") {
    return (
      <div className="flex flex-wrap gap-2">
        {REACTION_TYPES.map((type) => (
          <ReactionChip
            key={type.key}
            emoji={type.emoji}
            isLoggedIn={!!user}
            onRequireLogin={openLogin}
          />
        ))}
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={() => (user ? single.toggle() : openLogin())}
      aria-pressed={single.active}
      className={cn(
        "inline-flex w-fit items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm transition-colors",
        single.active
          ? "border-primary bg-primary/10 text-primary"
          : "border-input text-foreground hover:bg-accent",
      )}
    >
      <HeartIcon className="h-4 w-4" aria-hidden />
      <span>{single.count}</span>
    </button>
  );
}
