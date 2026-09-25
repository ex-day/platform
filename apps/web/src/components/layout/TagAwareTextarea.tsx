// see docs/ui/functions/F04-tag-suggest.md, C26-post-thread.md, C27-post-new.md
//
// C26(みんなの声)とC27(新しい話を始める)で共有する入力欄。タグは本文と同じ欄で付け、
// 専用のタグ欄は設けない(DEC-0009 決定10)。
// - 「#」のあとに、保管されているタグをよく使われている順に補完する(照合だけ。入力中にAIは使わない。DEC-0003)
// - 入力欄の中で本文中の#タグを強調する。投稿後の表示(BodyWithTags)と判定・見た目を共有し、
//   入力したときの見た目と投稿後の見た目をそろえる(Issue #67「判断：タグの入力と表示」)
// - 文中の言葉からのチップは廃止した(同判断)
//
// 強調の方式: textareaの後ろに同じ文字を持つ表示用の層(aria-hidden)を重ね、#タグの部分だけ色を付ける。
// textareaは文字色を透明にしてキャレットだけ表示する。contenteditableやエディタのライブラリは使わない。
// 重ね合わせがずれないよう、次をtextareaと表示用の層でそろえる。
// - 箱: FIELD_BOX_CLASS(border・padding・文字サイズ・行の高さ)を共有し、フォント・文字間隔は親から継承する
// - 折り返し: white-space: pre-wrap / overflow-wrap: break-word / word-break: normal を明示する
// - スクロールバーの幅: scrollbar-gutter: stable を両方に付け、折り返し幅を一致させる
// - スクロール: textareaのscrollTopを表示用の層へ同期する(入力・スクロールのたび)
// - 末尾の改行: 表示用の層の末尾に空白を補い、最終行の高さが不足しないようにする
// - 強調の見た目は色と背景だけにし、太さは変えない(太さを変えると文字幅が変わり、キャレットとずれるため)
// - 日本語の変換中(IME)は、textareaの文字をそのまま表示し、表示用の層を隠す。変換中の文節の背景を
//   ブラウザ・IMEがtextarea側に描くため、透明な文字のままだと変換中の文字が見えなくなる。確定後に強調へ戻る
"use client";

import Link from "next/link";
import { useLayoutEffect, useRef, useState } from "react";
import { activeHashQuery, splitByTags, suggestTags, tagSearchHref } from "@/lib/mock-data/tags";
import { cn } from "@/lib/utils";

/** #タグの見た目。入力欄の強調と投稿後の表示で共有する(文字幅を変えない指定だけにする) */
export const TAG_TEXT_CLASS = "rounded-sm bg-blue-50 text-blue-700 dark:bg-blue-400/15 dark:text-blue-300";

/** textareaと表示用の層で完全に一致させる箱の指定 */
const FIELD_BOX_CLASS =
  "block w-full rounded-md border px-3 py-2 text-sm leading-6 [font:inherit] [letter-spacing:inherit] whitespace-pre-wrap break-words [word-break:normal] [scrollbar-gutter:stable] box-border";

function TagHighlightedText({ text }: { text: string }) {
  return (
    <>
      {splitByTags(text).map((segment, i) =>
        segment.isTag ? (
          <span key={i} className={TAG_TEXT_CLASS}>
            {segment.text}
          </span>
        ) : (
          segment.text
        ),
      )}
    </>
  );
}

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
  const mirrorRef = useRef<HTMLDivElement>(null);
  const [caret, setCaret] = useState(0);
  const [composing, setComposing] = useState(false);
  const hash = activeHashQuery(value, caret);
  const suggestions = hash ? suggestTags(hash.query) : [];

  const syncScroll = () => {
    if (ref.current && mirrorRef.current) {
      mirrorRef.current.scrollTop = ref.current.scrollTop;
      mirrorRef.current.scrollLeft = ref.current.scrollLeft;
    }
  };
  // 入力で内容・高さが変わった後にも、表示用の層のスクロール位置を合わせる
  useLayoutEffect(syncScroll, [value]);

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
    <div className="relative">
      <div className="relative rounded-md bg-background">
        <div
          ref={mirrorRef}
          aria-hidden
          data-testid="tag-highlight-layer"
          className={cn(
            FIELD_BOX_CLASS,
            "pointer-events-none absolute inset-0 h-full overflow-hidden border-transparent text-foreground",
            composing && "invisible",
          )}
        >
          <TagHighlightedText text={value} />
          {/* 末尾が改行のとき最終行の高さが不足しないよう、空白を補う */}
          {value.endsWith("\n") ? "\u00a0" : null}
        </div>
        <textarea
          ref={ref}
          value={value}
          onChange={(event) => {
            onChange(event.target.value);
            setCaret(event.target.selectionStart ?? event.target.value.length);
          }}
          onSelect={(event) => setCaret(event.currentTarget.selectionStart ?? 0)}
          onScroll={syncScroll}
          onCompositionStart={() => setComposing(true)}
          onCompositionEnd={() => setComposing(false)}
          placeholder={placeholder}
          aria-label={ariaLabel}
          rows={rows}
          spellCheck={false}
          className={cn(
            FIELD_BOX_CLASS,
            "relative resize-y overflow-y-auto border-input bg-transparent caret-foreground placeholder:text-muted-foreground selection:bg-blue-200/60 focus:border-ring focus:outline-none focus:ring-2 focus:ring-ring/40",
            composing ? "text-foreground" : "text-transparent selection:text-transparent",
            className,
          )}
        />
      </div>
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
  );
}

/** 投稿後の本文。#タグを入力欄の強調と同じ判定・見た目で表示する(押下でタグを条件にS02へ) */
export function BodyWithTags({ body }: { body: string }) {
  return (
    <p className="whitespace-pre-wrap break-words text-sm leading-6">
      {splitByTags(body).map((segment, i) =>
        segment.isTag ? (
          <Link key={i} href={tagSearchHref(segment.text.slice(1))} className={cn(TAG_TEXT_CLASS, "hover:underline")}>
            {segment.text}
          </Link>
        ) : (
          segment.text
        ),
      )}
    </p>
  );
}
