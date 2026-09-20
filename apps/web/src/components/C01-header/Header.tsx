// see docs/ui/components/C01-header.md
import Link from "next/link";
import { Search } from "@/components/C06-search/Search";
import { UserMenu } from "@/components/C07-dropdown-menu/UserMenu";

export function Header() {
  return (
    <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-3 px-4 py-2 sm:h-16 sm:flex-nowrap sm:gap-4 sm:py-0">
        <Link href="/" className="shrink-0 text-lg font-bold tracking-tight">
          ex-day
        </Link>
        <div className="order-3 w-full min-w-0 sm:order-none sm:flex-1">
          <Search />
        </div>
        <div className="ml-auto shrink-0"><UserMenu /></div>
      </div>
    </header>
  );
}
