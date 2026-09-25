// see docs/ui/components/C27-post-new.md, docs/ui/components/C01-header.md
//
// ヘッダーの「話す」から開いたとき、/posts/newを横取りして、開く前の画面の上にモーダルで表示する
// (Next.jsのParallel Routes + Intercepting Routes)。URLを直接開いた場合はapp/posts/new/page.tsxを表示する。
import { PostNewModal } from "@/components/C27-post-new/PostNewModal";

export default function PostNewInterceptedPage() {
  return <PostNewModal />;
}
