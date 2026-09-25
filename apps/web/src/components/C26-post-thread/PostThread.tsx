// see docs/ui/components/C26-post-thread.md, C23-media-source-dialog.md, DEC-0005, DEC-0009, DEC-0010
//
// S03に埋め込む「みんなの声」。会話(Post。画面では「声」)を時系列に並べ、その場で声を寄せられる。
// API未接続のため、送った声・出典の登録はこのコンポーネントの状態としてのみ保持し、再読み込みで失われる。
// - ツリー表示にせず、返信の関係は返信先を小さく引用して示す(Issue #67「判断：派生の誘導の見せ方」)
// - 派生の誘導: 会話の中の目印(派生が確定した位置)、続きの投稿への注記(見立て)、返信するときの提案
// - 直近n件(RECENT_COUNT)を常時表示し、それ以前は「過去のやりとりをすべて見る」で展開する。
//   n=2はIssue #48のWireframeでの試作値であり、確定値ではない(C26検討事項)。
// - 資料の添付時にC23を開かず、出典状態="unset"のまま添付する。出典表示自体を
//   ボタンとし、送る前・送った後のいつでもC23を任意に呼び出せる。
// - 未ログインで「送る」を押すとC08でログインを求め、ログイン後に入力内容のまま送る(DEC-0010 決定2)。
// - 表示名は表示のたびに投稿者の今のニックネームを引く(Issue #74)。モックではログインユーザーの声を
//   S08(ユーザー情報更新)で変えたニックネームで表示する(Issue #79)。
"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState, type ChangeEvent } from "react";
import {
  ArrowRightIcon,
  CornerDownRightIcon,
  FileIcon,
  FilmIcon,
  ImageIcon,
  PaperclipIcon,
  SparklesIcon,
  XIcon,
} from "lucide-react";
import { Button, buttonVariants } from "@/components/primitives/button";
import { MediaSourceDialog } from "@/components/C23-media-source-dialog/MediaSourceDialog";
import { ReactionButton } from "@/components/C10-reaction-button/ReactionButton";
import { BodyWithTags, TagAwareTextarea } from "@/components/layout/TagAwareTextarea";
import { useMockAuth } from "@/lib/mock-auth";
import { MOCK_SELF_USER_ID, resolveAuthorName } from "@/lib/mock-data/user";
import { cn } from "@/lib/utils";
import {
  describeSourceStatus,
  filesToDraftMedia,
  type DraftMedia,
} from "@/lib/mock-data/media";
import type { DerivationMarker, DiscoveryPost, DiscoveryRef } from "@/lib/mock-data/discovery";

const RECENT_COUNT = 2;

function sourceLabel(media: DraftMedia): string {
  const status =
    media.sourceStatus === "registered" && media.sourceName
      ? media.sourceName
      : media.sourceStatus === "unknown"
        ? "不明"
        : describeSourceStatus(media.sourceStatus);
  return `出典：${status}（${media.sourceStatus === "unset" ? "登録する" : "編集する"}）`;
}

function MediaItem({
  media,
  onEditSource,
  onRemove,
}: {
  media: DraftMedia;
  onEditSource: () => void;
  onRemove?: () => void;
}) {
  return (
    <div className="flex items-center gap-3 rounded-md border px-2 py-2">
      <div className="flex h-12 w-16 shrink-0 items-center justify-center overflow-hidden rounded bg-muted">
        {media.previewUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={media.previewUrl} alt="" className="h-full w-full object-cover" />
        ) : media.kind === "video" ? (
          <FilmIcon className="h-5 w-5 text-muted-foreground" aria-hidden />
        ) : media.kind === "image" ? (
          <ImageIcon className="h-5 w-5 text-muted-foreground" aria-hidden />
        ) : (
          <FileIcon className="h-5 w-5 text-muted-foreground" aria-hidden />
        )}
      </div>
      <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
        <span className="max-w-full truncate text-sm">{media.name}</span>
        <button
          type="button"
          onClick={onEditSource}
          className={cn(
            "rounded border px-1.5 text-left text-xs hover:border-primary hover:text-primary",
            media.sourceStatus === "unset" ? "border-dashed text-muted-foreground" : "",
          )}
        >
          {sourceLabel(media)}
        </button>
      </div>
      {onRemove ? (
        <Button type="button" variant="outline" size="sm" onClick={onRemove}>
          削除
        </Button>
      ) : null}
    </div>
  );
}

function excerpt(text: string, max = 40) {
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

/** 返信先を小さく引用する(Post間の参照。PostReference) */
function ReplyQuote({ target, onJump }: { target: DiscoveryPost; onJump?: () => void }) {
  const content = (
    <>
      <CornerDownRightIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
      <span className="min-w-0">
        <span className="font-medium">{target.author}</span>
        <span className="ml-1">{excerpt(target.body || "（資料）")}</span>
      </span>
    </>
  );
  const className = "flex w-fit max-w-full items-start gap-1 rounded border-l-2 bg-muted/50 px-2 py-1 text-left text-xs text-muted-foreground";
  return onJump ? (
    <button type="button" onClick={onJump} className={cn(className, "hover:text-foreground")} aria-label={`返信先：${target.author}の声へ移動`}>
      {content}
    </button>
  ) : (
    <div className={className}>{content}</div>
  );
}

/** 会話の中の目印。派生が確定した時点の位置に挟み、位置は動かさない */
function DerivationMarkerCard({ discovery }: { discovery: DiscoveryRef }) {
  return (
    <li className="py-3">
      <Link
        href={`/discoveries/${discovery.id}`}
        className="flex items-center justify-between gap-2 rounded-md border border-primary/40 bg-primary/5 px-3 py-2 text-sm hover:bg-primary/10"
      >
        <span className="flex items-center gap-2">
          <SparklesIcon className="h-4 w-4 text-primary" aria-hidden />
          ここから『{discovery.title}』が生まれました
        </span>
        <ArrowRightIcon className="h-4 w-4 shrink-0" aria-hidden />
      </Link>
    </li>
  );
}

type ActiveMedia = { postId: string | null; mediaId: string };

export function PostThread({
  initialPosts,
  derivationMarkers = [],
  derivedOriginNotice,
}: {
  initialPosts: DiscoveryPost[];
  derivationMarkers?: DerivationMarker[];
  derivedOriginNotice?: DiscoveryRef;
}) {
  const { user, profile, requireLogin } = useMockAuth();
  const [storedPosts, setPosts] = useState(initialPosts);
  const posts = storedPosts.map((p) => ({ ...p, author: resolveAuthorName(p, profile) }));
  const [expanded, setExpanded] = useState(false);
  const [body, setBody] = useState("");
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [dismissedSuggestion, setDismissedSuggestion] = useState(false);
  const [pendingMedia, setPendingMedia] = useState<DraftMedia[]>([]);
  // postId=nullは入力欄で添付中(未送信)の資料を指す
  const [activeMedia, setActiveMedia] = useState<ActiveMedia | null>(null);
  const fileInputId = useId();
  const objectUrls = useRef<string[]>([]);

  useEffect(() => {
    const urls = objectUrls.current;
    return () => urls.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const olderCount = Math.max(posts.length - RECENT_COUNT, 0);
  const older = posts.slice(0, olderCount);
  const recent = posts.slice(olderCount);
  const canSend = body.trim().length > 0 || pendingMedia.length > 0;
  const findPost = (id: string | undefined | null) => (id ? posts.find((p) => p.id === id) : undefined);

  // 返信先が派生した話題の声なら、派生先で話すことを提案する(強制しない。DEC-0009 決定8)
  const replyTarget = findPost(replyTo);
  const suggestion: DiscoveryRef | undefined = replyTarget
    ? replyTarget.continuedIn ?? derivationMarkers.find((m) => m.afterPostId === replyTarget.id)?.discovery
    : undefined;

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const added = filesToDraftMedia(files);
    added.forEach((m) => m.previewUrl && objectUrls.current.push(m.previewUrl));
    setPendingMedia((prev) => [...prev, ...added]);
  };

  const send = (author: { id: string; name: string }) => {
    setPosts((prev) => [
      ...prev,
      {
        id: `post-${Date.now()}`,
        author: author.name,
        authorId: author.id === MOCK_SELF_USER_ID ? author.id : undefined,
        postedAtLabel: "たった今",
        body: body.trim(),
        media: pendingMedia,
        replyTo: replyTo ?? undefined,
      },
    ]);
    setBody("");
    setPendingMedia([]);
    setReplyTo(null);
    setDismissedSuggestion(false);
  };

  // 未ログインで送ろうとした場合は、C08でログインを求め、ログイン後に入力内容のまま送る(DEC-0010 決定2)
  const handleSend = () => {
    if (!canSend) return;
    requireLogin((loggedIn) => send(loggedIn));
  };

  const jumpTo = (id: string) => {
    if (older.some((p) => p.id === id)) setExpanded(true);
    requestAnimationFrame(() => {
      document.getElementById(`post-${id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
    });
  };

  const findActiveMedia = (): DraftMedia | undefined => {
    if (!activeMedia) return undefined;
    const list =
      activeMedia.postId === null ? pendingMedia : findPost(activeMedia.postId)?.media ?? [];
    return list.find((m) => m.id === activeMedia.mediaId);
  };

  const applySourcePatch = (patch: Partial<DraftMedia>) => {
    if (!activeMedia) return;
    const update = (list: DraftMedia[]) =>
      list.map((m) => (m.id === activeMedia.mediaId ? { ...m, ...patch } : m));
    if (activeMedia.postId === null) {
      setPendingMedia(update);
    } else {
      setPosts((prev) =>
        prev.map((p) => (p.id === activeMedia.postId ? { ...p, media: update(p.media) } : p)),
      );
    }
    setActiveMedia(null);
  };

  const renderPost = (post: DiscoveryPost) => {
    const quoted = findPost(post.replyTo);
    const markers = derivationMarkers.filter((m) => m.afterPostId === post.id);
    return [
      <li key={post.id} id={`post-${post.id}`} className="flex flex-col gap-2 border-b py-3">
        <div className="flex flex-wrap items-baseline gap-2">
          <span className="text-sm font-semibold">{post.author}</span>
          <span className="text-xs text-muted-foreground">{post.postedAtLabel}</span>
          {post.isOrigin ? (
            // 起点の示し方はC26で未定義(Issue #53)。モック確認用の仮表示
            <span className="rounded-full border px-2 text-[11px] text-muted-foreground">はじまりの声</span>
          ) : null}
        </div>
        {quoted ? <ReplyQuote target={quoted} onJump={() => jumpTo(quoted.id)} /> : null}
        {post.body ? <BodyWithTags body={post.body} /> : null}
        {post.media.length > 0 ? (
          <div className="flex flex-col gap-2 sm:max-w-md">
            {post.media.map((media) => (
              <MediaItem
                key={media.id}
                media={media}
                onEditSource={() => setActiveMedia({ postId: post.id, mediaId: media.id })}
              />
            ))}
          </div>
        ) : null}
        {post.continuedIn ? (
          // 続きの投稿への注記。投稿後の解析による見立てのため、控えめに表示する
          <Link
            href={`/discoveries/${post.continuedIn.id}`}
            className="w-fit text-xs text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
          >
            『{post.continuedIn.title}』で続いています →
          </Link>
        ) : null}
        <div className="flex items-center gap-3">
          <ReactionButton variant="single" initialCount={0} />
          <button
            type="button"
            onClick={() => {
              setReplyTo(post.id);
              setDismissedSuggestion(false);
            }}
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            返信する
          </button>
        </div>
      </li>,
      ...markers.map((m) => <DerivationMarkerCard key={`marker-${m.discovery.id}`} discovery={m.discovery} />),
    ];
  };

  const activeMediaItem = findActiveMedia();

  return (
    <section className="flex flex-col gap-2" aria-labelledby="post-thread-heading">
      <h2 id="post-thread-heading" className="text-base font-semibold">
        みんなの声<span className="ml-1 text-sm font-normal text-muted-foreground">（{posts.length}件の声）</span>
      </h2>

      {derivedOriginNotice ? (
        <Link
          href={`/discoveries/${derivedOriginNotice.id}`}
          className="w-fit rounded-md border border-dashed px-3 py-2 text-xs text-muted-foreground hover:text-foreground"
        >
          『{derivedOriginNotice.title}』の会話から生まれた話です →
        </Link>
      ) : null}

      {olderCount > 0 ? (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          className="w-fit text-sm font-medium text-primary underline-offset-2 hover:underline"
        >
          {expanded ? "過去のやりとりを閉じる" : `過去のやりとりをすべて見る（${olderCount}件）`}
        </button>
      ) : null}

      {posts.length === 0 ? (
        <p className="py-3 text-sm text-muted-foreground">
          まだ声は寄せられていません。気づいたことや疑問、手元の資料を寄せてみませんか？
        </p>
      ) : (
        <ol className="flex flex-col">
          {expanded ? older.flatMap(renderPost) : null}
          {recent.flatMap(renderPost)}
        </ol>
      )}

      <div className="sticky bottom-0 z-10 -mx-4 flex flex-col gap-2 border-t bg-background px-4 py-3 sm:mx-0 sm:rounded-md sm:border">
        {replyTarget ? (
          <div className="flex items-start justify-between gap-2">
            <ReplyQuote target={replyTarget} />
            <button
              type="button"
              onClick={() => setReplyTo(null)}
              aria-label="返信をやめる"
              className="rounded p-1 text-muted-foreground hover:bg-accent"
            >
              <XIcon className="h-3.5 w-3.5" aria-hidden />
            </button>
          </div>
        ) : null}
        {suggestion && !dismissedSuggestion ? (
          <div role="note" className="flex flex-col gap-2 rounded-md border border-primary/40 bg-primary/5 px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between">
            <span>続きは『{suggestion.title}』で話しませんか？</span>
            <span className="flex shrink-0 gap-2">
              <Link href={`/discoveries/${suggestion.id}`} className={buttonVariants({ size: "sm" })}>
                『{excerpt(suggestion.title, 12)}』で話す
              </Link>
              <Button type="button" size="sm" variant="outline" onClick={() => setDismissedSuggestion(true)}>
                ここで送る
              </Button>
            </span>
          </div>
        ) : null}
        {pendingMedia.length > 0 ? (
          <div className="flex flex-col gap-2">
            {pendingMedia.map((media) => (
              <MediaItem
                key={media.id}
                media={media}
                onEditSource={() => setActiveMedia({ postId: null, mediaId: media.id })}
                onRemove={() => setPendingMedia((prev) => prev.filter((m) => m.id !== media.id))}
              />
            ))}
          </div>
        ) : null}
        <TagAwareTextarea
          value={body}
          onChange={setBody}
          placeholder="この話に声を寄せる（「#」でタグを付けられます）"
          ariaLabel="この話に声を寄せる"
          rows={2}
        />
        <div className="flex items-center justify-between gap-2">
          <label
            htmlFor={fileInputId}
            className={cn(buttonVariants({ variant: "outline", size: "sm" }), "cursor-pointer")}
          >
            <PaperclipIcon className="h-4 w-4" aria-hidden />
            資料・画像を追加する
          </label>
          <input
            id={fileInputId}
            type="file"
            multiple
            accept="image/*,video/*,application/pdf"
            className="hidden"
            onChange={(event: ChangeEvent<HTMLInputElement>) => {
              handleFiles(event.target.files);
              event.target.value = "";
            }}
          />
          <Button type="button" size="sm" onClick={handleSend} disabled={!canSend}>
            送る
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          出典はあとからいつでも登録できます。{user ? null : "送るときにログインをお願いします（入力内容はそのまま送れます）。"}
        </p>
      </div>

      {activeMediaItem ? (
        <MediaSourceDialog
          media={activeMediaItem}
          onClose={() => setActiveMedia(null)}
          onSubmit={applySourcePatch}
        />
      ) : null}
    </section>
  );
}
