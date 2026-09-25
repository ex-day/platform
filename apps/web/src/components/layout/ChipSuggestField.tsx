// 補完から選んでチップで追加し、×で外す入力欄(複数可)。
// S08(ユーザー情報更新)の興味のある地域・興味のある〇〇で共用する。画面固有の組み立て用のため、
// コンポーネントIDは付けていない(IDが必要か、付ける場合の候補はIssue #79のレビューで確認する。DEC-0011)。
"use client";

import { useId, useState, type KeyboardEvent, type ReactNode } from "react";
import { XIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export type ChipItem = { id: string; label: string };

export type SuggestOption = {
  id: string;
  label: ReactNode;
  /** 読み上げ・aria用の文字列 */
  text: string;
  onSelect: () => void;
};

export function ChipSuggestField({
  label,
  chips,
  onRemove,
  query,
  onQueryChange,
  options,
  placeholder,
  footnote,
  describedBy,
}: {
  label: string;
  chips: ChipItem[];
  onRemove: (id: string) => void;
  query: string;
  onQueryChange: (value: string) => void;
  options: SuggestOption[];
  placeholder: string;
  /** 候補の一覧の最後に添える注記 */
  footnote?: string;
  describedBy?: string;
}) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const showList = open && query.trim().length > 0;
  const current = Math.min(activeIndex, Math.max(options.length - 1, 0));

  const select = (option: SuggestOption) => {
    option.onSelect();
    setActiveIndex(0);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing) return;
    if (event.key === "ArrowDown" && options.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((current + 1) % options.length);
    } else if (event.key === "ArrowUp" && options.length > 0) {
      event.preventDefault();
      setActiveIndex((current - 1 + options.length) % options.length);
    } else if (event.key === "Enter") {
      // フォームの送信(「更新」)にしない
      event.preventDefault();
      if (showList && options[current]) select(options[current]);
    } else if (event.key === "Escape") {
      setOpen(false);
    }
  };

  return (
    <div className="flex flex-col gap-2">
      {chips.length > 0 ? (
        <ul aria-label={`${label}（登録済み）`} className="flex flex-wrap gap-2">
          {chips.map((chip) => (
            <li key={chip.id} className="flex items-center gap-1 rounded-full border py-1 pl-3 pr-1.5 text-sm">
              {chip.label}
              <button
                type="button"
                onClick={() => onRemove(chip.id)}
                aria-label={`${chip.label}を外す`}
                className="rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-foreground"
              >
                <XIcon className="h-3.5 w-3.5" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="relative sm:max-w-sm">
        <input
          type="text"
          role="combobox"
          aria-label={`${label}を追加`}
          aria-autocomplete="list"
          aria-expanded={showList}
          aria-controls={listId}
          aria-activedescendant={showList && options[current] ? `${listId}-${current}` : undefined}
          aria-describedby={describedBy}
          value={query}
          placeholder={placeholder}
          onChange={(event) => {
            onQueryChange(event.target.value);
            setOpen(true);
            setActiveIndex(0);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={onKeyDown}
          className="h-10 w-full rounded-md border bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        {showList ? (
          <ul
            id={listId}
            role="listbox"
            aria-label={`${label}の候補`}
            className="absolute left-0 right-0 z-20 mt-1 rounded-md border bg-popover p-1 text-sm text-popover-foreground shadow-md"
          >
            {options.length === 0 ? (
              <li className="px-3 py-2 text-muted-foreground">候補がありません</li>
            ) : (
              options.map((option, index) => (
                <li
                  key={option.id}
                  id={`${listId}-${index}`}
                  role="option"
                  aria-selected={index === current}
                  aria-label={option.text}
                  // blurより先に選べるようにする
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => select(option)}
                  onMouseEnter={() => setActiveIndex(index)}
                  className={cn("cursor-pointer rounded px-3 py-2", index === current && "bg-accent")}
                >
                  {option.label}
                </li>
              ))
            )}
            {footnote ? <li role="presentation" className="px-3 pb-1 pt-2 text-xs text-muted-foreground">{footnote}</li> : null}
          </ul>
        ) : null}
      </div>
    </div>
  );
}
