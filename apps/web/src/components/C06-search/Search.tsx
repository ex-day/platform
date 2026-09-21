// see docs/ui/components/C06-search.md
// Issue #31: どの画面からでもS02(/discoveries)へ遷移し、S02上で実行した場合は同画面の一覧を更新する
// (Issue #28の人間判断)。ダミーのため条件は自然文のqのみ。位置・範囲・時間・詳細条件はUI比較用で未接続。
"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ChevronDownIcon, MapPinIcon } from "lucide-react";

export function Search() {
  const router = useRouter();
  const [q, setQ] = useState("");
  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const query = q.trim();
    router.push(query ? `/discoveries?entry=search&q=${encodeURIComponent(query)}` : "/discoveries?entry=search");
  }
  return <form role="search" onSubmit={onSubmit} className="flex w-full items-center gap-1.5 text-xs md:max-w-4xl md:gap-2 md:text-sm">
    <button type="button" className="flex h-9 shrink-0 items-center gap-1 rounded-md border px-2 hover:bg-accent md:px-3"><MapPinIcon className="h-3.5 w-3.5" aria-hidden /><span className="md:hidden">現在地</span><span className="hidden md:inline">検索対象位置</span></button>
    <button type="button" className="h-9 shrink-0 rounded-md border px-2 hover:bg-accent md:px-3">範囲</button>
    <button type="button" className="h-9 shrink-0 rounded-md border px-2 hover:bg-accent md:px-3">時間</button>
    <label className="min-w-0 flex-1"><span className="sr-only">自然文で検索</span><input type="text" value={q} onChange={(e) => setQ(e.target.value)} placeholder="自然文で検索" className="h-9 w-full rounded-md border bg-background px-2 outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring md:px-3" /></label>
    <button type="submit" className="h-9 shrink-0 rounded-md bg-primary px-3 text-primary-foreground hover:bg-primary/90">検索</button>
    <button type="button" aria-label="詳細条件" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border hover:bg-accent md:w-auto md:rounded-md md:px-3"><span className="hidden md:inline">詳細条件</span><ChevronDownIcon className="h-4 w-4 md:hidden" aria-hidden /></button>
  </form>;
}
