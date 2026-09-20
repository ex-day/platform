// see docs/ui/components/C08-login-dialog.md
//
// 利用する認証サービス・認証プロバイダは未確定(検討事項)のため、
// モックではプロバイダ名のボタンを表示するのみで実際の認証は行わない。
"use client";

import Link from "next/link";
import { Button } from "@/components/primitives/button";
import { useMockAuth } from "@/lib/mock-auth";

const PROVIDERS = ["Google", "GitHub"] as const;

export function LoginDialog() {
  const { isLoginDialogOpen, closeLogin, login } = useMockAuth();

  if (!isLoginDialogOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="ログイン"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={closeLogin}
    >
      <div
        className="w-full max-w-sm rounded-lg border bg-background p-6 shadow-lg"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="text-base font-semibold">ログイン</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          モックのため、実際の認証は行われません。
        </p>
        <div className="mt-4 flex flex-col gap-2">
          {PROVIDERS.map((provider) => (
            <Button
              key={provider}
              variant="outline"
              onClick={() => login(provider)}
            >
              {provider}でログイン（モック）
            </Button>
          ))}
        </div>
        <div className="mt-4 flex items-center justify-between text-sm">
          <Link
            href="/signup"
            className="text-primary underline underline-offset-2"
          >
            新規ユーザー登録
          </Link>
          <button
            type="button"
            onClick={closeLogin}
            className="text-muted-foreground hover:underline"
          >
            閉じる
          </button>
        </div>
      </div>
    </div>
  );
}
