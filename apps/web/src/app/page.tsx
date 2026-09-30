// see docs/ui/screens/S01-top.md, docs/ui/wireframe/screens/S01/*.png
//
// S01(Discovery提案)。API のデータで表示する(Issue #113)。セクション(C11)はブラウザ側で取る。
// - 「もっと見る」(S02への移動)とヘッダーの検索は、今回は外す(#89)。
// - モックのデータの S01(比較用バー付き)は /mock に残した。
import { Header } from "@/components/C01-header/Header";
import { Footer } from "@/components/C02-footer/Footer";
import { DiscoverySections } from "@/components/C11-discovery-sections/DiscoverySections";

export default function Home() {
  return <><Header showSearch={false} /><main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:py-8"><DiscoverySections /></main><Footer /></>;
}
