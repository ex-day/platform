// see docs/ui/components/C24-time-period-input.md
//
// 当初はDialog内で利用するが、C24自体の責務は入力とその情報の受け渡しとし、
// Dialog専用には固定しない(呼び出し元がDialogの開閉を用意する)。
"use client";

import { useState } from "react";
import { Button } from "@/components/primitives/button";
import { Dialog } from "@/components/primitives/dialog";
import { describeTimePeriod, type TimePeriodValue } from "@/lib/mock-data/contribution-draft";

export function TimePeriodField({
  value,
  onChange,
}: {
  value: TimePeriodValue | null;
  onChange: (value: TimePeriodValue | null) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
          詳しく指定
        </Button>
      </div>
      <p className="max-w-md rounded-md border border-input bg-background px-3 py-2 text-sm text-muted-foreground">
        詳細な時期・期間・時刻：{describeTimePeriod(value)}
      </p>
      {open ? (
        <TimePeriodDialog
          initial={value}
          onClose={() => setOpen(false)}
          onSet={(next) => {
            onChange(next);
            setOpen(false);
          }}
          onClear={() => {
            onChange(null);
            setOpen(false);
          }}
        />
      ) : null}
    </div>
  );
}

function TimePeriodDialog({
  initial,
  onClose,
  onSet,
  onClear,
}: {
  initial: TimePeriodValue | null;
  onClose: () => void;
  onSet: (value: TimePeriodValue) => void;
  onClear: () => void;
}) {
  const [periodType, setPeriodType] = useState<"approximate" | "specific" | undefined>(initial?.periodType);
  const [approximatePeriod, setApproximatePeriod] = useState(initial?.approximatePeriod ?? "");
  const [specificPeriod, setSpecificPeriod] = useState(initial?.specificPeriod ?? "");
  const [timeFrom, setTimeFrom] = useState(initial?.timeFrom ?? "");
  const [timeTo, setTimeTo] = useState(initial?.timeTo ?? "");
  const [text, setText] = useState(initial?.text ?? "");

  const draft: TimePeriodValue = {
    periodType,
    approximatePeriod: approximatePeriod || undefined,
    specificPeriod: specificPeriod || undefined,
    timeFrom: timeFrom || undefined,
    timeTo: timeTo || undefined,
    text: text || undefined,
  };

  return (
    <Dialog open onClose={onClose} label="時期・期間を詳しく指定">
      <h2 className="text-base font-semibold">時期・期間を詳しく指定</h2>

      <div className="mt-4 flex flex-col gap-1.5">
        <span className="text-xs font-medium text-muted-foreground">時期・日付の指定方法</span>
        <div className="flex flex-wrap gap-2">
          {([
            ["approximate", "毎年・おおよその時期"],
            ["specific", "特定の日付・期間"],
          ] as const).map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={periodType === value}
              onClick={() => setPeriodType(value)}
              className={
                periodType === value
                  ? "rounded-full border border-foreground bg-foreground px-3 py-1 text-xs font-medium text-background"
                  : "rounded-full border border-input px-3 py-1 text-xs font-medium hover:bg-accent"
              }
            >
              {label}
            </button>
          ))}
        </div>
        <p className="text-xs text-muted-foreground">曖昧な場合はいずれかを強制しなくても構いません。</p>
      </div>

      {periodType === "approximate" ? (
        <label className="mt-3 flex flex-col gap-1 text-sm">
          毎年・おおよその時期
          <input
            value={approximatePeriod}
            onChange={(event) => setApproximatePeriod(event.target.value)}
            placeholder="例：4月初旬、7/15〜8/31"
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </label>
      ) : null}

      {periodType === "specific" ? (
        <label className="mt-3 flex flex-col gap-1 text-sm">
          特定の日付・期間
          <input
            value={specificPeriod}
            onChange={(event) => setSpecificPeriod(event.target.value)}
            placeholder="例：2027/4/3、2027/4/3〜2027/4/10"
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </label>
      ) : null}

      <div className="mt-3 flex gap-2">
        <label className="flex flex-1 flex-col gap-1 text-sm">
          時間From
          <input
            value={timeFrom}
            onChange={(event) => setTimeFrom(event.target.value)}
            placeholder="例：17:00"
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </label>
        <label className="flex flex-1 flex-col gap-1 text-sm">
          時間To
          <input
            value={timeTo}
            onChange={(event) => setTimeTo(event.target.value)}
            placeholder="例：21:00"
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          />
        </label>
      </div>

      <label className="mt-3 flex flex-col gap-1 text-sm">
        時期・時間の自由入力・補足
        <textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="例：7/15〜8/末、例年4月初旬くらい、日暮れから"
          className="min-h-16 rounded-md border border-input bg-background px-3 py-2 text-sm"
        />
      </label>

      <p className="mt-3 rounded-md border border-input bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
        入力内容の確認: {describeTimePeriod(draft)}
      </p>

      <div className="mt-6 flex flex-wrap justify-end gap-2 border-t pt-4">
        <Button type="button" variant="ghost" size="sm" onClick={onClear}>
          詳細指定を解除
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onClose}>
          キャンセル
        </Button>
        <Button type="button" size="sm" onClick={() => onSet(draft)}>
          設定
        </Button>
      </div>
    </Dialog>
  );
}
