// see docs/ui/components/C01-header.md
import Link from "next/link";
import { Search } from "@/components/C06-search/Search";
import { UserMenu } from "@/components/C07-dropdown-menu/UserMenu";

export function Header() {
  return (
    <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-5xl items-center gap-4 px-4">
        <Link href="/" className="shrink-0 text-lg font-bold tracking-tight">
          ex-day
        </Link>
        <div className="min-w-0 flex-1">
          <Search />
        </div>
        <UserMenu />
      </div>
    </header>
  );
}
