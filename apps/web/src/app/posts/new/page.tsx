// see docs/ui/components/C27-post-new.md
//
// /posts/newを直接開いた場合(再読み込み・共有を含む)の表示。モーダルと同じ内容を、
// 背景の画面なしで表示する。閉じるとS01(Discovery提案)へ戻る。
import { Footer } from "@/components/C02-footer/Footer";
import { Header } from "@/components/C01-header/Header";
import { PostNewStandalone } from "@/components/C27-post-new/PostNewModal";

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

export default async function PostNewPage({ searchParams }: Props) {
  const sp = await searchParams;
  const similar = (Array.isArray(sp.similar) ? sp.similar[0] : sp.similar) === "none" ? "none" : "some";
  return (
    <>
      <Header />
      <main className="mx-auto w-full max-w-lg flex-1 px-4 py-6">
        <div className="rounded-lg border p-6 shadow-sm">
          <PostNewStandalone similarCount={similar} />
        </div>
      </main>
      <Footer />
    </>
  );
}
