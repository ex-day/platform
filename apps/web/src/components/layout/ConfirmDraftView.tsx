// see docs/ui/screens/S11-contribution-save-confirm.md(未着手。Issue #40),
// docs/decisions/DEC-0003-s04-s11-ai-analysis-boundary.md
//
// S11自体は本Issueのスコープ外(Wireframe未着手)。本コンポーネントは、S04が
// AI解析の完了を待たずに入力内容を引き継いで遷移したことを確認するための
// 暫定表示(sessionStorageのhandoffをそのまま表示するだけで、解析は行わない)。
"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  SEASON_OPTIONS,
  TIME_OF_DAY_OPTIONS,
  describeLocation,
  describeSourceStatus,
  describeTimePeriod,
  readHandoffDraft,
  type ContributionDraft,
} from "@/lib/mock-data/contribution-draft";

function label(options: { value: string; label: string }[], value: string): string {
  return options.find((o) => o.value === value)?.label ?? value;
}

export function ConfirmDraftView({ draftId }: { draftId?: string }) {
  const [state, setState] = useState<"loading" | "found" | "missing">("loading");
  const [draft, setDraft] = useState<ContributionDraft | null>(null);

  useEffect(() => {
    // sessionStorageからの取得結果はマイクロタスク内(外部システムから取得した
    // 値を受け取るコールバック)で反映する
    queueMicrotask(() => {
      if (!draftId) {
        setState("missing");
        return;
      }
      const found = readHandoffDraft(draftId);
      setDraft(found);
      setState(found ? "found" : "missing");
    });
  }, [draftId]);

  const resumeHref = draft
    ? draft.mode === "edit" && draft.editId
      ? `/contributions/${draft.editId}/edit?resume=${draftId}`
      : `/contributions/new?resume=${draftId}`
    : "/contributions/new";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-lg font-bold">S11 知識・疑問登録確認画面</h1>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
          Mock・画面遷移確認用の暫定表示
        </span>
      </div>

      <p className="rounded-md border border-dashed bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        S04はAI解析Function（contribution-analysis、仮称）の呼び出し・完了を待たずにこの画面へ遷移しました
        (DEC-0003)。解析結果の確認・提示はS11のWireframe/Mock（Issue #40）で扱います。
      </p>

      {state === "loading" ? <p className="text-sm text-muted-foreground">受け取った内容を確認しています…</p> : null}

      {state === "missing" ? (
        <p className="text-sm text-muted-foreground">
          受け取った入力内容が見つかりませんでした（このページへ直接アクセスした場合、または保存期限切れの場合）。
        </p>
      ) : null}

      {state === "found" && draft ? (
        <div className="flex flex-col gap-4 rounded-md border p-4">
          <Row label="状態" value={draft.mode === "new" ? "新規作成" : `編集（元ID: ${draft.editId}）`} />
          <Row label="投稿種別" value={draft.contributionType === "knowledge" ? "知識" : "疑問"} />
          {draft.originDiscovery ? <Row label="遷移元Discovery" value={draft.originDiscovery.title} /> : null}
          <Row label="本文" value={draft.body || "(未入力)"} multiline />
          <Row
            label="資料"
            value={
              draft.media.length === 0
                ? "なし"
                : `${draft.media.length}件（${draft.media
                    .map((m) => `${m.name}: ${describeSourceStatus(m.sourceStatus)}`)
                    .join("、")}）`
            }
          />
          <Row label="場所" value={describeLocation(draft.place)} />
          <Row
            label="いつの話ですか？"
            value={
              draft.timeAnswer
                ? `${{ current: "現在", past: "過去", future: "未来", unknown: "不明" }[draft.timeAnswer]}${
                    draft.timeAnswer === "past" && draft.timeDetail ? `（${draft.timeDetail}）` : ""
                  }`
                : "未回答"
            }
          />
          <Row
            label="いつ見たり体験したりできますか？"
            value={
              draft.seasons.length === 0 && draft.timesOfDay.length === 0
                ? "未指定"
                : [
                    ...draft.seasons.map((s) => label(SEASON_OPTIONS, s)),
                    ...draft.timesOfDay.map((t) => label(TIME_OF_DAY_OPTIONS, t)),
                  ].join("、")
            }
          />
          <Row label="詳細な時期・期間・時刻" value={describeTimePeriod(draft.timePeriod)} />
        </div>
      ) : null}

      <div className="flex justify-start border-t pt-4">
        <Link href={resumeHref} className="text-sm font-medium text-primary underline-offset-2 hover:underline">
          入力内容を修正する
        </Link>
      </div>
    </div>
  );
}

function Row({ label, value, multiline }: { label: string; value: string; multiline?: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <p className={multiline ? "whitespace-pre-wrap text-sm" : "text-sm"}>{value}</p>
    </div>
  );
}
