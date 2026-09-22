import type { ReactNode } from "react";
import { Header } from "@/components/C01-header/Header";
import { Footer } from "@/components/C02-footer/Footer";
import { LoginDialog } from "@/components/C08-login-dialog/LoginDialog";
import { MockAuthProvider } from "@/lib/mock-auth";

export default function ContributionNewLayout({ children }: { children: ReactNode }) {
  return (
    <MockAuthProvider>
      <Header />
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">{children}</main>
      <Footer />
      <LoginDialog />
    </MockAuthProvider>
  );
}
