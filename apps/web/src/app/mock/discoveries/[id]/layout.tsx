import type { ReactNode } from "react";
import { Header } from "@/components/C01-header/Header";
import { Footer } from "@/components/C02-footer/Footer";

export default function DiscoveryDetailLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        {children}
      </main>
      <Footer />
    </>
  );
}
