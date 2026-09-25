// モック用の簡易認証状態。実際の認証プロバイダ・認証方式は別途検討する
// (docs/ui/components/C08-login-dialog.md 検討事項)。ページ再読み込みで状態は失われる。
"use client";

import {
  createContext,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

type MockUser = { name: string };

type MockAuthContextValue = {
  user: MockUser | null;
  isLoginDialogOpen: boolean;
  login: (providerName: string) => void;
  logout: () => void;
  openLogin: () => void;
  closeLogin: () => void;
  /**
   * ログインが必要な操作(投稿の確定等)を、ログイン後に続けて行う。未ログインならC08を開き、
   * ログインしたらonLoggedInを呼ぶ。ログインせずに閉じた場合は呼ばない(DEC-0010 決定2)。
   */
  requireLogin: (onLoggedIn: (user: MockUser) => void) => void;
};

const MockAuthContext = createContext<MockAuthContextValue | null>(null);

export function MockAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MockUser | null>(null);
  const [isLoginDialogOpen, setLoginDialogOpen] = useState(false);
  const pendingRef = useRef<((user: MockUser) => void) | null>(null);

  const value = useMemo<MockAuthContextValue>(
    () => ({
      user,
      isLoginDialogOpen,
      login: (providerName: string) => {
        const next = { name: `モックユーザー(${providerName})` };
        setUser(next);
        setLoginDialogOpen(false);
        const pending = pendingRef.current;
        pendingRef.current = null;
        pending?.(next);
      },
      logout: () => setUser(null),
      openLogin: () => setLoginDialogOpen(true),
      closeLogin: () => {
        pendingRef.current = null;
        setLoginDialogOpen(false);
      },
      requireLogin: (onLoggedIn) => {
        if (user) {
          onLoggedIn(user);
          return;
        }
        pendingRef.current = onLoggedIn;
        setLoginDialogOpen(true);
      },
    }),
    [user, isLoginDialogOpen],
  );

  return (
    <MockAuthContext.Provider value={value}>
      {children}
    </MockAuthContext.Provider>
  );
}

export function useMockAuth() {
  const ctx = useContext(MockAuthContext);
  if (!ctx) {
    throw new Error("useMockAuth must be used within MockAuthProvider");
  }
  return ctx;
}
