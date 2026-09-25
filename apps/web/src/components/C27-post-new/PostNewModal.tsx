// see docs/ui/components/C27-post-new.md
//
// C27をモーダル(PC)／全画面のシート(モバイル)として表示するシェル。閉じるとrouter.back()で
// 開く前の画面へ戻る。Escで閉じる。背景の画面は残したまま表示する。
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { PostNew } from "@/components/C27-post-new/PostNew";
import { useMockAuth } from "@/lib/mock-auth";

export function PostNewModal() {
  const router = useRouter();
  const { isLoginDialogOpen } = useMockAuth();

  useEffect(() => {
    // C08(ログイン)を上に開いている間のEscはC08を閉じるだけにする
    if (isLoginDialogOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") router.back();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [router, isLoginDialogOpen]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="新しい話を始める"
      className="fixed inset-0 z-40 flex items-stretch justify-center bg-black/50 sm:items-center sm:p-4"
      onClick={() => router.back()}
    >
      <div
        className="h-full w-full overflow-y-auto bg-background p-5 shadow-lg sm:h-auto sm:max-h-[90vh] sm:max-w-lg sm:rounded-lg sm:border sm:p-6"
        onClick={(event) => event.stopPropagation()}
      >
        <PostNew onClose={() => router.back()} />
      </div>
    </div>
  );
}

/** /posts/newを直接開いた場合の表示。閉じるとS01へ戻る */
export function PostNewStandalone({ similarCount }: { similarCount?: "some" | "none" }) {
  const router = useRouter();
  return <PostNew onClose={() => router.push("/")} similarCount={similarCount} />;
}
