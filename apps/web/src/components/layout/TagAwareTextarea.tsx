// see docs/ui/functions/F04-tag-suggest.md, C26-post-thread.md, C27-post-new.md
//
// C26(みんなの声)とC27(新しい話を始める)で共有する入力欄。タグは本文と同じ欄で付け、
// 専用のタグ欄は設けない(DEC-0009 決定10)。
// - 「#」のあとに、保管されているタグをよく使われている順に補完する
// - 文中の言葉のうち既存のタグに一致するものを入力欄の下にチップで表示し、タップでタグにする
// 候補はタグとの照合だけで求め、入力中にAIは使わない(DEC-0003)。
"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { HashIcon } from "lucide-react";
import {
  activeHashQuery,
  applyTagChip,
  suggestTags,
  tagChipsFromText,
  tagSearchHref,
} from "@/lib/mock-data/tags";
import { cn } from "@/lib/utils";

export function TagAwareTextarea({
  value,
  onChange,
  placeholder,
  ariaLabel,
  rows = 3,
  className,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  ariaLabel: string;
  rows?: number;
  className?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const [caret, setCaret] = useState(0);
  const hash = activeHashQuery(value, caret);
  const suggestions = hash ? suggestTags(hash.query) : [];
  const chips = tagChipsFromText(value);

  const pickSuggestion = (name: string) => {
    if (!hash) return;
    const next = `${value.slice(0, hash.start)}#${name} ${value.slice(caret)}`;
    const nextCaret = hash.start + name.length + 2;
    onChange(next);
    setCaret(nextCaret);
    requestAnimationFrame(() => {
      ref.current?.focus();
      ref.current?.setSelectionRange(nextCaret, nextCaret);
    });
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="relative">
        <textarea
          ref={ref}
          value={value}
          onChange={(event) => {
            onChange(event.target.value);
            setCaret(event.target.selectionStart ?? event.target.value.length);
          }}
          onSelect={(event) => setCaret(event.currentTarget.selectionStart ?? 0)}
          placeholder={placeholder}
          aria-label={ariaLabel}
          rows={rows}
          className={cn(
            "w-full rounded-md border border-input bg-background px-3 py-2 text-sm",
            className,
          )}
        />
        {suggestions.length > 0 ? (
          <ul
            role="listbox"
            aria-label="タグの候補"
            className="absolute left-0 right-0 top-full z-20 mt-1 max-h-48 overflow-y-auto rounded-md border bg-popover p-1 text-sm shadow-md"
          >
            {suggestions.map((tag) => (
              <li key={tag.name}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => pickSuggestion(tag.name)}
                  className="flex w-full items-center justify-between rounded px-2 py-1.5 text-left hover:bg-accent"
                >
                  <span>#{tag.name}</span>
                  <span className="text-xs text-muted-foreground">{tag.count}件</span>
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
      {chips.length > 0 ? (
        <div className="flex flex-wrap items-center gap-1.5" aria-label="タグにできる言葉">
          <span className="text-xs text-muted-foreground">タグにする：</span>
          {chips.map((tag) => (
            <button
              key={tag.name}
              type="button"
              onClick={() => onChange(applyTagChip(value, tag.name))}
              className="inline-flex items-center gap-0.5 rounded-full border border-dashed px-2 py-0.5 text-xs hover:border-primary hover:text-primary"
            >
              <HashIcon className="h-3 w-3" aria-hidden />
              {tag.name}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** 本文中の「#タグ」を見分けられる形で表示する */
export function BodyWithTags({ body }: { body: string }) {
  const parts = body.split(/(#[^\s#、。,.!?！？「」()（）]+)/g);
  return (
    <p className="whitespace-pre-line text-sm leading-relaxed">
      {parts.map((part, i) =>
        part.startsWith("#") && part.length > 1 ? (
          <Link key={i} href={tagSearchHref(part.slice(1))} className="font-medium text-blue-700 hover:underline">
            {part}
          </Link>
        ) : (
          part
        ),
      )}
    </p>
  );
}
