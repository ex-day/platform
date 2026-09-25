import type { Metadata } from "next";
import type { ReactNode } from "react";
import { LoginDialog } from "@/components/C08-login-dialog/LoginDialog";
import { MockAuthProvider } from "@/lib/mock-auth";
import "./globals.css";

export const metadata: Metadata = {
  title: "ex-day",
  description: "ex-day mockup",
};

// @modal: ヘッダーの「話す」から開くC27(新しい話を始める)のモーダル(app/@modal/(.)posts/new)。
// モーダルと背景の画面でログイン状態を共有するため、MockAuthProviderとC08をここに置く。
export default function RootLayout({ children, modal }: { children: ReactNode; modal: ReactNode }) {
  return (
    <html lang="ja" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <MockAuthProvider>
          {children}
          {modal}
          <LoginDialog />
        </MockAuthProvider>
      </body>
    </html>
  );
}
