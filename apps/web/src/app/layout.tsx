import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ex-day",
  description: "ex-day mockup",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ja" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
