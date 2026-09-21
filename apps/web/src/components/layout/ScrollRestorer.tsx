// S02: S03へ遷移して戻ったとき、一覧のスクロール位置を復元する(Issue #28の人間判断)。
// カード(S03へのリンク)押下時にscrollYを保存し、同じURLのS02が再表示されたときに1回だけ復元する。
// 時間を置いて戻った場合の順位・鮮度・再取得の基準は実装時に決定する(モックでは常に復元)。
"use client";

import { useEffect } from "react";

const KEY = "s02-scroll";

export function ScrollRestorer() {
  useEffect(() => {
    const url = location.pathname + location.search;
    try {
      const saved = JSON.parse(sessionStorage.getItem(KEY) ?? "null") as { url: string; y: number } | null;
      if (saved?.url === url) {
        sessionStorage.removeItem(KEY);
        // Next.jsの遷移後スクロール(先頭へ)より後に実行する
        setTimeout(() => window.scrollTo(0, saved.y), 50);
      }
    } catch {}
    function onClick(event: MouseEvent) {
      const a = (event.target as HTMLElement).closest("a");
      if (!a || !/^\/discoveries\/[^/?]+/.test(a.getAttribute("href") ?? "")) return;
      try { sessionStorage.setItem(KEY, JSON.stringify({ url, y: window.scrollY })); } catch {}
    }
    document.addEventListener("click", onClick);
    return () => document.removeEventListener("click", onClick);
  }, []);
  return null;
}
