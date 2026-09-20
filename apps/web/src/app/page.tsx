// see docs/ui/screens/S01-top.md, docs/ui/wireframe/screens/S01/*.png
import { Header } from "@/components/C01-header/Header";
import { Footer } from "@/components/C02-footer/Footer";
import { LoginDialog } from "@/components/C08-login-dialog/LoginDialog";
import { DiscoverySections } from "@/components/C11-discovery-sections/DiscoverySections";
import { MockAuthProvider } from "@/lib/mock-auth";

export default function Home() {
  return <MockAuthProvider><Header /><main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:py-8"><DiscoverySections /></main><Footer /><LoginDialog /></MockAuthProvider>;
}
