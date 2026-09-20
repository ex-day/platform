// see docs/ui/components/C07-dropdown-menu.md
"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { MenuIcon, UserIcon } from "lucide-react";
import { useMockAuth } from "@/lib/mock-auth";

export function UserMenu() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { user, logout, openLogin } = useMockAuth();

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        aria-label="ユーザーメニュー"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex h-9 w-9 items-center justify-center rounded-full border hover:bg-accent"
      >
        {user ? (
          <UserIcon className="h-5 w-5" aria-hidden />
        ) : (
          <MenuIcon className="h-5 w-5" aria-hidden />
        )}
      </button>
      {open ? (
        <div
          role="menu"
          className="absolute right-0 z-40 mt-2 w-56 rounded-md border bg-popover p-1 text-sm text-popover-foreground shadow-md"
        >
          {user ? (
            <>
              <div className="px-3 py-2 text-xs text-muted-foreground">
                {user.name}さん
              </div>
              <Link
                href="/account/contributions"
                role="menuitem"
                className="block rounded px-3 py-2 hover:bg-accent"
              >
                自分の投稿一覧
              </Link>
              <Link
                href="/account"
                role="menuitem"
                className="block rounded px-3 py-2 hover:bg-accent"
              >
                ユーザー情報更新
              </Link>
              <Link
                href="/contributions/new"
                role="menuitem"
                className="block rounded px-3 py-2 hover:bg-accent"
              >
                新規知識登録
              </Link>
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  logout();
                  setOpen(false);
                }}
                className="block w-full rounded px-3 py-2 text-left hover:bg-accent"
              >
                ログアウト
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  openLogin();
                }}
                className="block w-full rounded px-3 py-2 text-left hover:bg-accent"
              >
                ログイン
              </button>
              <Link
                href="/signup"
                role="menuitem"
                className="block rounded px-3 py-2 hover:bg-accent"
              >
                新規ユーザー登録
              </Link>
            </>
          )}
          <div className="my-1 h-px bg-border" />
          <Link
            href="/about"
            role="menuitem"
            className="block rounded px-3 py-2 hover:bg-accent"
          >
            about
          </Link>
          <Link
            href="/support"
            role="menuitem"
            className="block rounded px-3 py-2 hover:bg-accent"
          >
            support
          </Link>
        </div>
      ) : null}
    </div>
  );
}
