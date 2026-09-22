// see docs/ui/screens/S04-contribution-save.md
//
// S04の画面内レイアウト。場所・時期の任意詳細セクションは新しいCxxを追加せず、
// 画面内レイアウトとして実装する(定義書「新しいCxxコンポーネントは定義しない」)。
// 新規登録(/contributions/new)と編集(/contributions/[id]/edit)の両ページから
// 同じフォームとして利用する。
"use client";

import { useEffect, useId, useRef, useState, type ChangeEvent, type DragEvent } from "react";
import { useRouter } from "next/navigation";
import { FileIcon, FilmIcon, ImageIcon } from "lucide-react";
import { Button, buttonVariants } from "@/components/primitives/button";
import { LocationTrigger } from "@/components/C22-location-input/LocationInput";
import { MediaSourceDialog } from "@/components/C23-media-source-dialog/MediaSourceDialog";
import { TimePeriodField } from "@/components/C24-time-period-input/TimePeriodInput";
import { useMockAuth } from "@/lib/mock-auth";
import { cn } from "@/lib/utils";
import {
  MOCK_FORBIDDEN_WORDS,
  SEASON_OPTIONS,
  TIME_ANSWER_OPTIONS,
  TIME_DETAIL_SUGGESTIONS,
  TIME_OF_DAY_OPTIONS,
  describeSourceStatus,
  readHandoffDraft,
  saveDraftForHandoff,
  type ContributionDraft,
  type ContributionKind,
  type DraftMedia,
  type MediaKind,
} from "@/lib/mock-data/contribution-draft";

function kindFromMime(mime: string): MediaKind {
  if (mime.startsWith("image/")) return "image";
  if (mime.startsWith("video/")) return "video";
  if (mime === "application/pdf") return "pdf";
  return "other";
}

/**
 * 不適切資料の簡易判定(モック上の仮ルール)。ファイル名(拡張子除く)が単語として
 * "ng"を含む場合に検証用として不適切とみなす。実際の検出方式は
 * Issue #35で確定する(docs/ui/screens/S04-contribution-save.md 検討事項)。
 */
function isFlaggedName(fileName: string): boolean {
  const stem = fileName.replace(/\.[^./]+$/, "");
  return /\bng\b/i.test(stem);
}

function filesToDraftMedia(files: FileList): DraftMedia[] {
  return Array.from(files).map((file) => {
    const kind = kindFromMime(file.type);
    return {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      name: file.name,
      kind,
      previewUrl: kind === "image" ? URL.createObjectURL(file) : undefined,
      sourceStatus: "unset",
      flaggedInappropriate: isFlaggedName(file.name),
    };
  });
}

const TYPE_OPTIONS: { value: ContributionKind; label: string }[] = [
  { value: "knowledge", label: "知識" },
  { value: "question", label: "疑問" },
];

export function ContributionEditor({
  initialDraft,
  initialDisclosureOpen = false,
  initialAuth,
  resumeHandoffId,
}: {
  initialDraft: ContributionDraft;
  initialDisclosureOpen?: boolean;
  initialAuth?: "guest" | "user";
  /**
   * S11(Mock)から「入力内容を修正する」で戻った場合に、確認へ進む直前の入力内容を
   * 復元するためのhandoff ID(DEC-0003「受け取って解析する」方式のモック実装)。
   */
  resumeHandoffId?: string;
}) {
  const router = useRouter();
  const { user, login, logout } = useMockAuth();
  const [draft, setDraft] = useState(initialDraft);
  const [disclosureOpen, setDisclosureOpen] = useState(initialDisclosureOpen);
  const [activeMediaDialogId, setActiveMediaDialogId] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ body?: string; media?: string }>({});
  const fileInputId = useId();
  const appliedAuthPreset = useRef(false);
  const appliedResume = useRef(false);

  useEffect(() => {
    if (appliedResume.current || !resumeHandoffId) return;
    appliedResume.current = true;
    // sessionStorageの読み取り結果を反映する。setState呼び出しは
    // マイクロタスク内(外部システムから取得した値を受け取るコールバック)で行う
    queueMicrotask(() => {
      const resumed = readHandoffDraft(resumeHandoffId);
      if (resumed) setDraft(resumed);
    });
  }, [resumeHandoffId]);

  // [Mock確認用] ?auth=guest|user で未ログイン/ログイン状態からの投稿開始を切り替える
  useEffect(() => {
    if (appliedAuthPreset.current || !initialAuth) return;
    appliedAuthPreset.current = true;
    if (initialAuth === "user" && !user) login("モック確認用");
    if (initialAuth === "guest" && user) logout();
  }, [initialAuth, user, login, logout]);

  useEffect(() => {
    return () => {
      draft.media.forEach((m) => {
        if (m.previewUrl) URL.revokeObjectURL(m.previewUrl);
      });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateDraft = (patch: Partial<ContributionDraft>) => setDraft((d) => ({ ...d, ...patch }));

  const handleFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    updateDraft({ media: [...draft.media, ...filesToDraftMedia(files)] });
  };

  const removeMedia = (id: string) => {
    const target = draft.media.find((m) => m.id === id);
    if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl);
    updateDraft({ media: draft.media.filter((m) => m.id !== id) });
  };

  const toggleSeason = (value: string) => {
    updateDraft({
      seasons: draft.seasons.includes(value)
        ? draft.seasons.filter((s) => s !== value)
        : [...draft.seasons, value],
    });
  };

  const toggleTimeOfDay = (value: string) => {
    updateDraft({
      timesOfDay: draft.timesOfDay.includes(value)
        ? draft.timesOfDay.filter((s) => s !== value)
        : [...draft.timesOfDay, value],
    });
  };

  const handleSubmit = () => {
    const hasBody = draft.body.trim().length > 0;
    const hasMedia = draft.media.length > 0;
    const forbidden = MOCK_FORBIDDEN_WORDS.find((w) => draft.body.includes(w));
    const inappropriate = draft.media.filter((m) => m.flaggedInappropriate);

    const nextErrors: { body?: string; media?: string } = {};
    if (!hasBody && !hasMedia) {
      nextErrors.body = "本文または資料の少なくとも一方を入力してください。";
      nextErrors.media = "本文または資料の少なくとも一方を入力してください。";
    }
    if (forbidden) {
      nextErrors.body = `禁止ワード「${forbidden}」が含まれています(検証用の仮リスト)。`;
    }
    if (inappropriate.length > 0) {
      nextErrors.media = `不適切と判定された資料があります: ${inappropriate
        .map((m) => m.name)
        .join("、")}(検証用の仮判定)`;
    }
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }
    setErrors({});
    const handoffId = saveDraftForHandoff(draft);
    router.push(`/contributions/new/confirm?draft=${handoffId}`);
  };

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6 pb-10">
      <h1 className="text-lg font-bold">
        {draft.mode === "new" ? "S04 知識・疑問登録（新規）" : "S04 知識・疑問編集"}
      </h1>

      {draft.originDiscovery ? (
        <div className="flex items-center gap-2 rounded-md border border-dashed border-destructive/60 bg-destructive/5 px-3 py-2 text-sm">
          <span className="rounded-full bg-destructive px-2 py-0.5 text-xs font-medium text-destructive-foreground">
            遷移元Discovery
          </span>
          <span className="text-muted-foreground">{draft.originDiscovery.title}についての投稿</span>
        </div>
      ) : null}

      {!user ? (
        <p className="rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          未ログインです。投稿確定時の認証タイミングは未確定ですが、確認へ進むまでは入力できます。
        </p>
      ) : null}

      <section className="flex flex-col gap-2">
        <div className="flex items-baseline gap-2">
          <span className="text-sm font-semibold">投稿種別</span>
          <span className="text-xs text-muted-foreground">contribution_type</span>
        </div>
        <div className="flex gap-2">
          {TYPE_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              aria-pressed={draft.contributionType === opt.value}
              disabled={draft.mode === "edit"}
              onClick={() => updateDraft({ contributionType: opt.value })}
              className={cn(
                "rounded-full border px-4 py-1.5 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-60",
                draft.contributionType === opt.value
                  ? "border-foreground bg-foreground text-background"
                  : "border-input hover:bg-accent",
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>
        {draft.mode === "edit" ? (
          <p className="text-xs text-muted-foreground">編集時の投稿種別の変更可否は未確定のため、変更不可としています。</p>
        ) : null}
      </section>

      <section className="flex flex-col gap-2">
        <div className="flex items-baseline gap-2">
          <span className="text-sm font-semibold">本文</span>
          <span className="text-xs text-muted-foreground">contribution_body</span>
          <span className="rounded-full bg-destructive px-2 py-0.5 text-xs text-destructive-foreground">必須</span>
        </div>
        <p className="text-xs text-muted-foreground">本文または資料の少なくとも一方が必須です</p>
        <textarea
          value={draft.body}
          onChange={(event) => updateDraft({ body: event.target.value })}
          placeholder="見つけたこと、気になったことを書いてみましょう"
          className="min-h-32 rounded-md border border-input bg-background px-3 py-2 text-sm"
        />
        {errors.body ? <p className="text-xs text-destructive">{errors.body}</p> : null}
      </section>

      <section className="flex flex-col gap-2">
        <div className="flex items-baseline gap-2">
          <span className="text-sm font-semibold">資料</span>
          <span className="text-xs text-muted-foreground">contribution_media</span>
        </div>
        <p className="text-xs text-muted-foreground">任意。画像・動画・PDF等（0..n）</p>
        <div
          onDragOver={(event: DragEvent<HTMLDivElement>) => event.preventDefault()}
          onDrop={(event: DragEvent<HTMLDivElement>) => {
            event.preventDefault();
            handleFiles(event.dataTransfer.files);
          }}
          className="flex flex-col gap-2 rounded-md border border-dashed p-3"
        >
          {draft.media.map((media) => (
            <div
              key={media.id}
              className={cn(
                "flex items-center gap-3 rounded-md border px-3 py-2",
                media.flaggedInappropriate ? "border-destructive" : "border-foreground/60",
              )}
            >
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
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate text-sm">{media.name}</span>
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    className={cn(
                      "rounded border px-1.5 text-xs",
                      media.sourceStatus === "unset" ? "border-dashed text-muted-foreground" : "",
                    )}
                  >
                    出典：{describeSourceStatus(media.sourceStatus)}
                  </span>
                  <button
                    type="button"
                    onClick={() => setActiveMediaDialogId(media.id)}
                    className="text-xs font-medium text-primary underline-offset-2 hover:underline"
                  >
                    出典を設定
                  </button>
                </div>
                {media.flaggedInappropriate ? (
                  <span className="text-xs text-destructive">不適切と判定されました(検証用の仮判定)</span>
                ) : null}
              </div>
              <Button type="button" variant="outline" size="sm" onClick={() => removeMedia(media.id)}>
                削除
              </Button>
            </div>
          ))}
          <label
            htmlFor={fileInputId}
            className={cn(buttonVariants({ variant: "outline", size: "sm" }), "w-fit cursor-pointer")}
          >
            ＋ 資料を追加
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
        </div>
        {errors.media ? <p className="text-xs text-destructive">{errors.media}</p> : null}
      </section>

      <section className="rounded-md border border-dashed bg-muted/30 p-3">
        <button
          type="button"
          onClick={() => setDisclosureOpen((v) => !v)}
          aria-expanded={disclosureOpen}
          className="flex w-full items-center justify-between gap-2 text-left"
        >
          <span className="flex flex-wrap items-baseline gap-2">
            <span className="text-sm font-semibold">場所・時期を詳しく伝える（任意）</span>
            <span className="text-xs text-muted-foreground">contribution_context_details</span>
          </span>
          <span className="shrink-0 text-xs text-muted-foreground">{disclosureOpen ? "▲ 閉じる" : "▼ 開く"}</span>
        </button>
        {!disclosureOpen ? (
          <p className="mt-2 text-xs text-muted-foreground">
            閉じたままでも投稿できます。開閉で入力内容は消えません。
          </p>
        ) : (
          <div className="mt-3 flex flex-col gap-5 border-t pt-3">
            <div className="flex flex-col gap-1.5">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-semibold">場所</span>
                <span className="text-xs text-muted-foreground">contribution_place</span>
              </div>
              <LocationTrigger value={draft.place} onChange={(place) => updateDraft({ place })} />
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-semibold">いつの話ですか？</span>
                <span className="text-xs text-muted-foreground">contribution_time_group</span>
              </div>
              <div className="flex flex-wrap gap-2">
                {TIME_ANSWER_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    aria-pressed={draft.timeAnswer === opt.value}
                    onClick={() =>
                      updateDraft({ timeAnswer: draft.timeAnswer === opt.value ? null : opt.value })
                    }
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs font-medium",
                      draft.timeAnswer === opt.value
                        ? "border-foreground bg-foreground text-background"
                        : "border-input hover:bg-accent",
                    )}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
              {draft.timeAnswer === "past" ? (
                <div className="mt-1 flex flex-col gap-1">
                  <div className="flex items-baseline gap-2">
                    <span className="text-xs font-medium">時代・年代の詳細</span>
                    <span className="text-xs text-muted-foreground">contribution_time_detail</span>
                  </div>
                  <p className="text-xs text-muted-foreground">検索候補＋自由入力。候補が無くても入力できる</p>
                  <input
                    list="time-detail-suggestions"
                    value={draft.timeDetail}
                    onChange={(event) => updateDraft({ timeDetail: event.target.value })}
                    placeholder="例：中世（戦国期）ごろ"
                    className="h-9 max-w-xs rounded-md border border-input bg-background px-3 text-sm"
                  />
                  <datalist id="time-detail-suggestions">
                    {TIME_DETAIL_SUGGESTIONS.map((s) => (
                      <option key={s} value={s} />
                    ))}
                  </datalist>
                </div>
              ) : null}
            </div>

            <div className="flex flex-col gap-2">
              <div className="flex items-baseline gap-2">
                <span className="text-sm font-semibold">いつ見たり体験したりできますか？</span>
                <span className="text-xs text-muted-foreground">contribution_experience_time_group</span>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted-foreground">季節</span>
                <div className="flex flex-wrap gap-2">
                  {SEASON_OPTIONS.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      aria-pressed={draft.seasons.includes(opt.value)}
                      onClick={() => toggleSeason(opt.value)}
                      className={cn(
                        "rounded-full border px-3 py-1 text-xs font-medium",
                        draft.seasons.includes(opt.value)
                          ? "border-foreground bg-foreground text-background"
                          : "border-input hover:bg-accent",
                      )}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted-foreground">時間帯</span>
                <div className="flex flex-wrap gap-2">
                  {TIME_OF_DAY_OPTIONS.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      aria-pressed={draft.timesOfDay.includes(opt.value)}
                      onClick={() => toggleTimeOfDay(opt.value)}
                      className={cn(
                        "rounded-full border px-3 py-1 text-xs font-medium",
                        draft.timesOfDay.includes(opt.value)
                          ? "border-foreground bg-foreground text-background"
                          : "border-input hover:bg-accent",
                      )}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>
              <TimePeriodField value={draft.timePeriod} onChange={(timePeriod) => updateDraft({ timePeriod })} />
            </div>
          </div>
        )}
      </section>

      <div className="flex justify-end border-t pt-4">
        <Button type="button" onClick={handleSubmit}>
          確認へ進む
        </Button>
      </div>

      {activeMediaDialogId ? (
        <MediaSourceDialog
          media={draft.media.find((m) => m.id === activeMediaDialogId)!}
          onClose={() => setActiveMediaDialogId(null)}
          onSubmit={(patch) => {
            updateDraft({
              media: draft.media.map((m) => (m.id === activeMediaDialogId ? { ...m, ...patch } : m)),
            });
            setActiveMediaDialogId(null);
          }}
        />
      ) : null}
    </div>
  );
}
