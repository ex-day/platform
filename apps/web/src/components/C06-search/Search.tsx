// see docs/ui/components/C06-search.md
//
// S03モックではHeader表示のためのプレースホルダーとして配置する。
// 検索・絞り込みの実際の挙動はC06の実装issueで検討する(このIssue #8のスコープ外)。
import { SearchIcon } from "lucide-react";

export function Search() {
  return (
    <div className="flex max-w-md items-center gap-2 rounded-md border border-input bg-background px-3 py-1.5 text-sm text-muted-foreground">
      <SearchIcon className="h-4 w-4 shrink-0" aria-hidden />
      <input
        type="text"
        placeholder="Discoveryを検索"
        disabled
        className="w-full bg-transparent outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
      />
    </div>
  );
}
