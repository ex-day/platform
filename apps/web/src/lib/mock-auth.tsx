// モック用の簡易認証状態。実際の認証プロバイダ・認証方式は別途検討する
// (docs/ui/components/C08-login-dialog.md 検討事項)。ページ再読み込みで状態は失われる。
"use client";

import {
  createContext,
  useContext,
  useMemo,
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
};

const MockAuthContext = createContext<MockAuthContextValue | null>(null);

export function MockAuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MockUser | null>(null);
  const [isLoginDialogOpen, setLoginDialogOpen] = useState(false);

  const value = useMemo<MockAuthContextValue>(
    () => ({
      user,
      isLoginDialogOpen,
      login: (providerName: string) => {
        setUser({ name: `モックユーザー(${providerName})` });
        setLoginDialogOpen(false);
      },
      logout: () => setUser(null),
      openLogin: () => setLoginDialogOpen(true),
      closeLogin: () => setLoginDialogOpen(false),
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
