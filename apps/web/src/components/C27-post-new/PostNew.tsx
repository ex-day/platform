// see docs/ui/components/C27-post-new.md(C27は番号の候補), DEC-0010, Issue #67
//
// 新しい話を始める投稿のフォーム。ヘッダーの「話す」から開くモーダル(app/@modal/(.)posts/new)と、
// URLを直接開いた場合のページ(app/posts/new)の両方で使う。
// - 見出しは「新しい話を始める」。種類・場所・時期の入力は求めない(DEC-0005)
// - タグは本文と同じ欄で付ける(「#」の補完と、入力欄の中の#タグの強調。F04)。投稿直後の本文も同じ見た目で表示する
// - 未ログインでも入力でき、「送る」でC08のログインを求め、ログイン後に入力内容のまま投稿する(DEC-0010 決定2)
// - 投稿直後に、似たDiscoveryとわかってきたことを示す(F03のモック。DEC-0010 決定1)。投稿前には止めない
// API未接続のため、投稿内容は保存しない。
"use client";

import Link from "next/link";
import { useId, useState, type ChangeEvent } from "react";
import { CheckCircle2Icon, FileIcon, PaperclipIcon, XIcon } from "lucide-react";
import { Button, buttonVariants } from "@/components/primitives/button";
import { BodyWithTags, TagAwareTextarea } from "@/components/layout/TagAwareTextarea";
import { useMockAuth } from "@/lib/mock-auth";
import { cn } from "@/lib/utils";
import { filesToDraftMedia, type DraftMedia } from "@/lib/mock-data/media";
import { MOCK_NEW_DISCOVERY_ID, MOCK_SIMILAR_DISCOVERIES } from "@/lib/mock-data/post-new";

type Props = {
  onClose: () => void;
  /** [Mock確認用] 投稿直後に似たDiscoveryが0件の場合を確認する */
  similarCount?: "some" | "none";
};

export function PostNew({ onClose, similarCount = "some" }: Props) {
  const { user, requireLogin } = useMockAuth();
  const [body, setBody] = useState("");
  const [media, setMedia] = useState<DraftMedia[]>([]);
  const [posted, setPosted] = useState<{ body: string } | null>(null);
  const fileInputId = useId();
  const canSend = body.trim().length > 0 || media.length > 0;

  const submit = () => setPosted({ body: body.trim() });

  // 未ログインで送ろうとした場合は、C08でログインを求め、ログイン後に入力内容のまま投稿する(DEC-0010 決定2)
  const handleSend = () => {
    if (!canSend) return;
    requireLogin(() => submit());
  };

  const header = (title: string) => (
    <div className="flex items-center justify-between gap-2">
      <h2 className="text-lg font-bold">{title}</h2>
      <button type="button" onClick={onClose} aria-label="閉じる" className="rounded p-1 hover:bg-accent">
        <XIcon className="h-5 w-5" aria-hidden />
      </button>
    </div>
  );

  if (posted) {
    const similar = similarCount === "some" ? MOCK_SIMILAR_DISCOVERIES : [];
    return (
      <div className="flex flex-col gap-4">
        {header("新しい話を始めました")}
        <div className="flex items-start gap-2 rounded-md bg-muted/50 px-3 py-2">
          <CheckCircle2Icon className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden />
          {posted.body ? <BodyWithTags body={posted.body} /> : <span className="text-sm">（資料のみ）</span>}
        </div>
        {similar.length > 0 ? (
          <section className="flex flex-col gap-3" aria-labelledby="similar-heading">
            <div>
              <h3 id="similar-heading" className="text-sm font-semibold">同じことを気にしている人がいます</h3>
              <p className="text-xs text-muted-foreground">こんなことがわかっています。加わるかどうかは、あなたが選べます。</p>
            </div>
            <ul className="flex flex-col gap-2">
              {similar.map((d) => (
                <li key={d.id} className="flex flex-col gap-2 rounded-md border px-3 py-2">
                  <span className="text-sm font-medium">{d.title}</span>
                  <ul className="flex flex-col gap-1">
                    {d.findings.map((f) => (
                      <li key={f.text} className="text-xs text-muted-foreground">
                        <span className="mr-1 rounded border px-1">{f.kind === "value" ? "価値" : "説"}</span>
                        {f.text}
                      </li>
                    ))}
                  </ul>
                  <Link href={`/discoveries/${d.id}`} className={cn(buttonVariants({ variant: "outline", size: "sm" }), "w-fit")}>
                    この話に加わる
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ) : null}
        <Link href={`/discoveries/${MOCK_NEW_DISCOVERY_ID}`} className={cn(buttonVariants(), "w-full")}>
          自分の話を見る
        </Link>
        <p className="text-xs text-muted-foreground">
          [Mock] 投稿は保存されません。遷移先は固定のモックです（あなたの話＝探索開始直後のS03）。
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {header("新しい話を始める")}
      <TagAwareTextarea
        value={body}
        onChange={setBody}
        placeholder="気になったこと、見つけたことを話してみましょう（「#」でタグを付けられます）"
        ariaLabel="新しい話の本文"
        rows={5}
      />
      {media.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {media.map((m) => (
            <li key={m.id} className="flex items-center justify-between gap-2 rounded-md border px-2 py-1.5 text-sm">
              <span className="flex min-w-0 items-center gap-2">
                <FileIcon className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                <span className="truncate">{m.name}</span>
                <span className="shrink-0 text-xs text-muted-foreground">出典：未設定（あとから登録できます）</span>
              </span>
              <Button type="button" size="sm" variant="outline" onClick={() => setMedia((prev) => prev.filter((x) => x.id !== m.id))}>
                削除
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
      <div className="flex items-center justify-between gap-2">
        <label htmlFor={fileInputId} className={cn(buttonVariants({ variant: "outline", size: "sm" }), "cursor-pointer")}>
          <PaperclipIcon className="h-4 w-4" aria-hidden />
          資料を追加
        </label>
        <input
          id={fileInputId}
          type="file"
          multiple
          accept="image/*,video/*,application/pdf"
          className="hidden"
          onChange={(event: ChangeEvent<HTMLInputElement>) => {
            if (event.target.files) setMedia((prev) => [...prev, ...filesToDraftMedia(event.target.files!)]);
            event.target.value = "";
          }}
        />
        <Button type="button" onClick={handleSend} disabled={!canSend}>
          送る
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        場所や時期がわからなくても大丈夫です。
        {user ? null : "送るときにログインをお願いします（入力内容はそのまま引き継ぎます）。"}
      </p>
    </div>
  );
}
