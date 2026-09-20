// see docs/ui/components/C06-search.md
// Issue #14では条件UIの比較に限定する。条件変更後の同画面更新/S02遷移は
// Non-blockingな判断待ちのため、検索結果を変更する処理は持たせない。
"use client";

import { ChevronDownIcon, MapPinIcon } from "lucide-react";

export function Search() {
  return <div className="flex w-full items-center gap-1.5 text-xs sm:max-w-4xl sm:gap-2 sm:text-sm">
    <button type="button" className="flex h-9 shrink-0 items-center gap-1 rounded-md border px-2 hover:bg-accent sm:px-3"><MapPinIcon className="h-3.5 w-3.5" aria-hidden /><span className="sm:hidden">現在地</span><span className="hidden sm:inline">検索対象位置</span></button>
    <button type="button" className="h-9 shrink-0 rounded-md border px-2 hover:bg-accent sm:px-3">範囲</button>
    <button type="button" className="h-9 shrink-0 rounded-md border px-2 hover:bg-accent sm:px-3">時間</button>
    <label className="min-w-0 flex-1"><span className="sr-only">自然文で検索</span><input type="text" placeholder="自然文で検索" className="h-9 w-full rounded-md border bg-background px-2 outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring sm:px-3" /></label>
    <button type="button" aria-label="詳細条件" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border hover:bg-accent sm:w-auto sm:rounded-md sm:px-3"><span className="hidden sm:inline">詳細条件</span><ChevronDownIcon className="h-4 w-4 sm:hidden" aria-hidden /></button>
  </div>;
}
