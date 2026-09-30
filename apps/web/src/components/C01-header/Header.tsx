// see docs/ui/components/C01-header.md
//
// 新規投稿の入口「話す」を置く(Issue #67「判断：新規投稿の動線」)。
// - アイコンは吹き出しを避け、ペン(新しく始める)にラベル「話す」を添える
// - 認証状態にかかわらず表示し、/posts/newへ移る。他の画面からはapp/@modal/(.)posts/newが
//   横取りしてモーダルで開く(URLを直接開いた場合はapp/posts/new)
// - PC・モバイルともヘッダーに置く(右下に浮かぶボタンにはしない)
import Link from "next/link";
import { PenLineIcon } from "lucide-react";
import { Search } from "@/components/C06-search/Search";
import { UserMenu } from "@/components/C07-dropdown-menu/UserMenu";
import { buttonVariants } from "@/components/primitives/button";
import { cn } from "@/lib/utils";

export const NEW_POST_HREF = "/posts/new";

export function NewPostEntryLink({ className }: { className?: string }) {
  return (
    <Link href={NEW_POST_HREF} className={cn(buttonVariants({ size: "sm" }), className)}>
      <PenLineIcon className="h-4 w-4" aria-hidden />
      話す
    </Link>
  );
}

// showSearch=false で検索(C06)を外す。API のデータの S01 では外している(#89、#113)
export function Header({ showSearch = true }: { showSearch?: boolean }) {
  return (
    <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-2 md:h-16 md:flex-nowrap md:gap-4 md:py-0">
        <Link href="/" className="shrink-0 text-lg font-bold tracking-tight">
          ex-day
        </Link>
        {showSearch ? (
          <div className="order-3 w-full min-w-0 md:order-none md:flex-1">
            <Search />
          </div>
        ) : null}
        <div className="ml-auto flex shrink-0 items-center gap-2">
          <NewPostEntryLink />
          <UserMenu />
        </div>
      </div>
    </header>
  );
}
