// see docs/ui/screens/S01-top.md, docs/ui/wireframe/screens/S01/*.png
//
// モックのデータの S01(表現案の比較用バー付き)。API のデータの S01 は / (Issue #113)。
import { Header } from "@/components/C01-header/Header";
import { Footer } from "@/components/C02-footer/Footer";
import { MockDiscoverySections } from "@/components/C11-discovery-sections/MockDiscoverySections";

export default function MockHome() {
  return <><Header /><main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:py-8"><MockDiscoverySections /></main><Footer /></>;
}
