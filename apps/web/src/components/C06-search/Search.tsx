// see docs/ui/components/C06-search.md
//
// S03モックではHeader表示のためのプレースホルダーとして配置する。
// 検索・絞り込みの実際の挙動はC06の実装issueで検討する(このIssue #8のスコープ外)。
// モバイルではワイヤーフレーム(docs/ui/wireframe/S03/Mobile-Main.png)に合わせ、
// アイコン起点の簡易表現とする(C06検討事項: モバイルでは主要検索条件を1行程度に収める)。
import { SearchIcon } from "lucide-react";

export function Search() {
  return (
    <>
      <button
        type="button"
        disabled
        aria-label="Discoveryを検索"
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border text-muted-foreground disabled:cursor-not-allowed sm:hidden"
      >
        <SearchIcon className="h-4 w-4" aria-hidden />
      </button>
      <div className="hidden max-w-md items-center gap-2 rounded-md border border-input bg-background px-3 py-1.5 text-sm text-muted-foreground sm:flex">
        <SearchIcon className="h-4 w-4 shrink-0" aria-hidden />
        <input
          type="text"
          placeholder="Discoveryを検索"
          disabled
          className="w-full bg-transparent outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
        />
      </div>
    </>
  );
}
