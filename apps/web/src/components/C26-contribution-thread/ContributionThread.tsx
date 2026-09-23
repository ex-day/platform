// see docs/ui/components/C26-contribution-thread.md, C23-media-source-dialog.md, DEC-0005
//
// S03に埋め込むコメント一覧と入力欄。API未接続のため、投稿・出典の登録はこの
// コンポーネントの状態としてのみ保持し、再読み込みで失われる。
// - 直近n件(RECENT_COUNT)を常時表示し、それ以前は「過去のやりとりをすべて見る」で展開する。
//   n=2はIssue #48のWireframeでの試作値であり、確定値ではない(C26検討事項)。
// - 資料の添付時にC23を開かず、出典状態="unset"のまま添付する。出典表示自体を
//   ボタンとし、投稿前・投稿後のいつでもC23を任意に呼び出せる。
//   出典登録・編集の権限(投稿者本人以外が行えるか)はC23の検討事項のため、
//   モックでは誰でも操作できる状態としている。
"use client";

import { useEffect, useId, useRef, useState, type ChangeEvent } from "react";
import { FileIcon, FilmIcon, ImageIcon, PaperclipIcon } from "lucide-react";
import { Button, buttonVariants } from "@/components/primitives/button";
import { MediaSourceDialog } from "@/components/C23-media-source-dialog/MediaSourceDialog";
import { useMockAuth } from "@/lib/mock-auth";
import { cn } from "@/lib/utils";
import {
  describeSourceStatus,
  filesToDraftMedia,
  type DraftMedia,
} from "@/lib/mock-data/contribution-draft";
import type { DiscoveryComment } from "@/lib/mock-data/discovery";

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

type ActiveMedia = { commentId: string | null; mediaId: string };

export function ContributionThread({ initialComments }: { initialComments: DiscoveryComment[] }) {
  const { user } = useMockAuth();
  const [comments, setComments] = useState(initialComments);
  const [expanded, setExpanded] = useState(false);
  const [body, setBody] = useState("");
  const [pendingMedia, setPendingMedia] = useState<DraftMedia[]>([]);
  // commentId=nullは入力欄で添付中(未投稿)の資料を指す
  const [activeMedia, setActiveMedia] = useState<ActiveMedia | null>(null);
  const fileInputId = useId();
  const objectUrls = useRef<string[]>([]);

  useEffect(() => {
    const urls = objectUrls.current;
    return () => urls.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const olderCount = Math.max(comments.length - RECENT_COUNT, 0);
  const older = comments.slice(0, olderCount);
  const recent = comments.slice(olderCount);
  const canPost = body.trim().length > 0 || pendingMedia.length > 0;

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const added = filesToDraftMedia(files);
    added.forEach((m) => m.previewUrl && objectUrls.current.push(m.previewUrl));
    setPendingMedia((prev) => [...prev, ...added]);
  };

  const handlePost = () => {
    if (!canPost) return;
    setComments((prev) => [
      ...prev,
      {
        id: `comment-${Date.now()}`,
        author: user?.name ?? "あなた",
        postedAtLabel: "たった今",
        body: body.trim(),
        media: pendingMedia,
      },
    ]);
    setBody("");
    setPendingMedia([]);
  };

  const findActiveMedia = (): DraftMedia | undefined => {
    if (!activeMedia) return undefined;
    const list =
      activeMedia.commentId === null
        ? pendingMedia
        : comments.find((c) => c.id === activeMedia.commentId)?.media ?? [];
    return list.find((m) => m.id === activeMedia.mediaId);
  };

  const applySourcePatch = (patch: Partial<DraftMedia>) => {
    if (!activeMedia) return;
    const update = (list: DraftMedia[]) =>
      list.map((m) => (m.id === activeMedia.mediaId ? { ...m, ...patch } : m));
    if (activeMedia.commentId === null) {
      setPendingMedia(update);
    } else {
      setComments((prev) =>
        prev.map((c) => (c.id === activeMedia.commentId ? { ...c, media: update(c.media) } : c)),
      );
    }
    setActiveMedia(null);
  };

  const renderComment = (comment: DiscoveryComment) => (
    <li key={comment.id} className="flex flex-col gap-2 border-b py-3 last:border-b-0">
      <div className="flex items-baseline gap-2">
        <span className="text-sm font-semibold">{comment.author}</span>
        <span className="text-xs text-muted-foreground">{comment.postedAtLabel}</span>
      </div>
      {comment.body ? <p className="whitespace-pre-line text-sm leading-relaxed">{comment.body}</p> : null}
      {comment.media.length > 0 ? (
        <div className="flex flex-col gap-2 sm:max-w-md">
          {comment.media.map((media) => (
            <MediaItem
              key={media.id}
              media={media}
              onEditSource={() => setActiveMedia({ commentId: comment.id, mediaId: media.id })}
            />
          ))}
        </div>
      ) : null}
    </li>
  );

  const activeMediaItem = findActiveMedia();

  return (
    <section className="flex flex-col gap-2" aria-labelledby="contribution-thread-heading">
      <h2 id="contribution-thread-heading" className="text-base font-semibold">
        コメント<span className="ml-1 text-sm font-normal text-muted-foreground">（{comments.length}）</span>
      </h2>

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

      {comments.length === 0 ? (
        <p className="py-3 text-sm text-muted-foreground">
          まだコメントはありません。気づいたことや疑問、手元の資料を寄せてみませんか？
        </p>
      ) : (
        <ol className="flex flex-col">
          {expanded ? older.map(renderComment) : null}
          {recent.map(renderComment)}
        </ol>
      )}

      <div className="sticky bottom-0 z-10 -mx-4 flex flex-col gap-2 border-t bg-background px-4 py-3 sm:mx-0 sm:rounded-md sm:border">
        {pendingMedia.length > 0 ? (
          <div className="flex flex-col gap-2">
            {pendingMedia.map((media) => (
              <MediaItem
                key={media.id}
                media={media}
                onEditSource={() => setActiveMedia({ commentId: null, mediaId: media.id })}
                onRemove={() => setPendingMedia((prev) => prev.filter((m) => m.id !== media.id))}
              />
            ))}
          </div>
        ) : null}
        <textarea
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder="気づいたこと、知っていること、疑問を書いてみましょう"
          aria-label="コメント"
          rows={2}
          className="rounded-md border border-input bg-background px-3 py-2 text-sm"
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
          <Button type="button" size="sm" onClick={handlePost} disabled={!canPost}>
            投稿する
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">出典はあとからいつでも登録できます。</p>
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
