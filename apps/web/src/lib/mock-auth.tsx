// モック用の簡易認証状態。実際の認証プロバイダ・認証方式は別途検討する
// (docs/ui/components/C08-login-dialog.md 検討事項)。ページ再読み込みで状態は失われる。
//
// 公開される名前はex-dayで自分で決めたニックネームだけとし、プロバイダから受け取る名前は使わない
// (Issue #75)。モックのログインユーザーは1人(lib/mock-data/user.ts)とし、プロフィール(ニックネーム・
// 興味のある地域・興味のある〇〇)はS08(ユーザー情報更新)で変更する。声の表示名はプロフィールの
// 今のニックネームを引いて表示するため、ログイン状態にかかわらずプロフィールを保持する(Issue #74)。
"use client";

import {
  createContext,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { INITIAL_PROFILE, MOCK_SELF_USER_ID, type MockProfile } from "@/lib/mock-data/user";

type MockUser = { id: string; name: string };

type MockAuthContextValue = {
  user: MockUser | null;
  profile: MockProfile;
  updateProfile: (profile: MockProfile) => void;
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
  const [loggedIn, setLoggedIn] = useState(false);
  const [profile, setProfile] = useState<MockProfile>(INITIAL_PROFILE);
  const user = useMemo<MockUser | null>(
    () => (loggedIn ? { id: MOCK_SELF_USER_ID, name: profile.nickname } : null),
    [loggedIn, profile.nickname],
  );
  const [isLoginDialogOpen, setLoginDialogOpen] = useState(false);
  const pendingRef = useRef<((user: MockUser) => void) | null>(null);

  const value = useMemo<MockAuthContextValue>(
    () => ({
      user,
      profile,
      updateProfile: setProfile,
      isLoginDialogOpen,
      // どのプロバイダでも同じモックユーザーとしてログインする(プロバイダの名前は取り込まない)
      login: () => {
        const next = { id: MOCK_SELF_USER_ID, name: profile.nickname };
        setLoggedIn(true);
        setLoginDialogOpen(false);
        const pending = pendingRef.current;
        pendingRef.current = null;
        pending?.(next);
      },
      logout: () => setLoggedIn(false),
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
    [user, profile, isLoginDialogOpen],
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
