// see docs/ui/components/C23-media-source-dialog.md
"use client";

import { useState } from "react";
import { Button } from "@/components/primitives/button";
import { Dialog } from "@/components/primitives/dialog";
import type { DraftMedia, MediaSourceStatus, MediaSourceType } from "@/lib/mock-data/media";

const SOURCE_TYPE_OPTIONS: { value: MediaSourceType; label: string }[] = [
  { value: "website", label: "Webサイト" },
  { value: "book", label: "書籍・雑誌" },
  { value: "document", label: "文書・史料" },
  { value: "provided", label: "他者から提供" },
  { value: "other", label: "その他" },
];

export function MediaSourceDialog({
  media,
  onClose,
  onSubmit,
}: {
  media: DraftMedia;
  onClose: () => void;
  onSubmit: (patch: Partial<DraftMedia>) => void;
}) {
  const [status, setStatus] = useState<MediaSourceStatus>(media.sourceStatus === "unset" ? "self" : media.sourceStatus);
  const [sourceType, setSourceType] = useState<MediaSourceType | undefined>(media.sourceType);
  const [sourceName, setSourceName] = useState(media.sourceName ?? "");
  const [sourceUrl, setSourceUrl] = useState(media.sourceUrl ?? "");
  const [sourceNote, setSourceNote] = useState(media.sourceNote ?? "");
  const [createdAt, setCreatedAt] = useState(media.createdAt ?? "");
  const [targetTime, setTargetTime] = useState(media.targetTime ?? "");

  const handleSubmit = () => {
    onSubmit({
      sourceStatus: status,
      sourceType: status === "registered" ? sourceType : undefined,
      sourceName: status === "registered" ? sourceName : undefined,
      sourceUrl: status === "registered" ? sourceUrl : undefined,
      sourceNote: status === "registered" ? sourceNote : undefined,
      createdAt: createdAt || undefined,
      targetTime: targetTime || undefined,
    });
  };

  return (
    <Dialog open onClose={onClose} label="出典を設定">
      <h2 className="text-base font-semibold">出典を設定</h2>
      <p className="mt-1 text-xs text-muted-foreground">対象資料: {media.name}</p>

      <div className="mt-4 flex flex-col gap-1.5">
        <span className="text-xs font-medium text-muted-foreground">出典状態</span>
        <div className="flex flex-wrap gap-2">
          {([
            ["self", "自分で撮影・作成"],
            ["registered", "出典を登録"],
            ["unknown", "出典不明"],
          ] as const).map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={status === value}
              onClick={() => setStatus(value)}
              className={
                status === value
                  ? "rounded-full border border-foreground bg-foreground px-3 py-1 text-xs font-medium text-background"
                  : "rounded-full border border-input px-3 py-1 text-xs font-medium hover:bg-accent"
              }
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {status === "registered" ? (
        <div className="mt-4 flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            出典の種類
            <select
              value={sourceType ?? ""}
              onChange={(event) => setSourceType(event.target.value as MediaSourceType)}
              className="h-9 rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="" disabled>
                選択してください
              </option>
              {SOURCE_TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            出典名
            <input
              value={sourceName}
              onChange={(event) => setSourceName(event.target.value)}
              placeholder="ページ名、書名、史料名、提供者等"
              className="h-9 rounded-md border border-input bg-background px-3 text-sm"
            />
          </label>
          {sourceType === "website" ? (
            <label className="flex flex-col gap-1 text-sm">
              出典URL
              <input
                value={sourceUrl}
                onChange={(event) => setSourceUrl(event.target.value)}
                placeholder="https://..."
                className="h-9 rounded-md border border-input bg-background px-3 text-sm"
              />
            </label>
          ) : null}
          <label className="flex flex-col gap-1 text-sm">
            出典補足
            <textarea
              value={sourceNote}
              onChange={(event) => setSourceNote(event.target.value)}
              placeholder="著者・発行者、発行年、ページ、所蔵元、管理番号等"
              className="min-h-16 rounded-md border border-input bg-background px-3 py-2 text-sm"
            />
          </label>
        </div>
      ) : status === "unknown" ? (
        <p className="mt-3 text-xs text-muted-foreground">出典が分からないことを明示した状態として設定します。</p>
      ) : null}

      <div className="mt-4 flex flex-col gap-3 border-t pt-4">
        <label className="flex flex-col gap-1 text-sm">
          撮影・作成日時（任意）
          <input
            value={createdAt}
            onChange={(event) => setCreatedAt(event.target.value)}
            placeholder="例：2026/06/01"
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          資料の対象年代・時期（任意）
          <input
            value={targetTime}
            onChange={(event) => setTargetTime(event.target.value)}
            placeholder="例：中世ごろ（撮影・作成日時とは別の、資料が記録・記述する対象年代）"
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </label>
      </div>

      <div className="mt-6 flex flex-wrap justify-end gap-2 border-t pt-4">
        <Button type="button" variant="outline" size="sm" onClick={onClose}>
          キャンセル
        </Button>
        <Button type="button" size="sm" onClick={handleSubmit}>
          設定
        </Button>
      </div>
    </Dialog>
  );
}
