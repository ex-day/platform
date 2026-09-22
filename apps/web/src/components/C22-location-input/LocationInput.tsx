// see docs/ui/components/C22-location-input.md
//
// Map APIには接続せず、地図・検索結果はモック用の固定候補で代替する
// (検討事項「Map API、住所検索、逆ジオコーディング...は実装設計時に決定する」)。
"use client";

import { useState } from "react";
import { MapPinIcon } from "lucide-react";
import { Button } from "@/components/primitives/button";
import { Dialog } from "@/components/primitives/dialog";
import { describeLocation, type LocationValue } from "@/lib/mock-data/contribution-draft";

type LocationKind = "point" | "area" | "unknown";
type InputMethod = "current" | "map" | "search";

const SEARCH_RESULTS: { name: string; address: string; lat: number; lng: number }[] = [
  { name: "小机城址", address: "横浜市港北区", lat: 35.51, lng: 139.6 },
  { name: "横浜駅", address: "横浜市西区", lat: 35.4658, lng: 139.6222 },
  { name: "△△山 登山道", address: "○○県○○市", lat: 35.2, lng: 136.8 },
];

function initialPicked(
  value: LocationValue | null,
): { name?: string; address?: string; lat?: number; lng?: number } | null {
  if (!value || value.kind === "unknown") return null;
  if (value.kind === "point") return { name: value.name, address: value.address, lat: value.lat, lng: value.lng };
  return { name: value.name, lat: value.lat, lng: value.lng };
}

export function LocationTrigger({
  value,
  onChange,
}: {
  value: LocationValue | null;
  onChange: (value: LocationValue | null) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-2 hover:underline"
      >
        <MapPinIcon className="h-4 w-4" aria-hidden />
        {describeLocation(value)}
      </button>
      {open ? (
        <LocationDialog
          initial={value}
          onClose={() => setOpen(false)}
          onSet={(next) => {
            onChange(next);
            setOpen(false);
          }}
          onUnset={() => {
            onChange(null);
            setOpen(false);
          }}
        />
      ) : null}
    </>
  );
}

function LocationDialog({
  initial,
  onClose,
  onSet,
  onUnset,
}: {
  initial: LocationValue | null;
  onClose: () => void;
  onSet: (value: LocationValue) => void;
  onUnset: () => void;
}) {
  const [kind, setKind] = useState<LocationKind>(initial?.kind ?? "point");
  const [method, setMethod] = useState<InputMethod>("search");
  const [query, setQuery] = useState("");
  const [picked, setPicked] = useState<{ name?: string; address?: string; lat?: number; lng?: number } | null>(
    initialPicked(initial),
  );
  const [areaType, setAreaType] = useState<"radius" | "named">(initial?.kind === "area" ? initial.areaType : "radius");
  const [radius, setRadius] = useState<string>(initial?.kind === "area" && initial.radius ? String(initial.radius) : "500");
  const [namedArea, setNamedArea] = useState(initial?.kind === "area" ? initial.namedArea ?? "" : "");

  const selectKind = (next: LocationKind) => {
    setKind(next);
    if (next === "unknown") setPicked(null);
  };

  const results = SEARCH_RESULTS.filter((r) => r.name.includes(query) || query === "");

  const handleSubmit = () => {
    if (kind === "unknown") {
      onSet({ kind: "unknown" });
      return;
    }
    if (kind === "point") {
      if (!picked?.name) return;
      onSet({ kind: "point", name: picked.name, address: picked.address, lat: picked.lat, lng: picked.lng });
      return;
    }
    onSet({
      kind: "area",
      name: picked?.name,
      areaType,
      lat: picked?.lat,
      lng: picked?.lng,
      radius: areaType === "radius" ? Number(radius) || undefined : undefined,
      namedArea: areaType === "named" ? namedArea : undefined,
    });
  };

  return (
    <Dialog open onClose={onClose} label="場所を設定">
      <h2 className="text-base font-semibold">場所を設定</h2>

      <div className="mt-4 flex flex-col gap-1.5">
        <span className="text-xs font-medium text-muted-foreground">場所の種類</span>
        <div className="flex flex-wrap gap-2">
          {([
            ["point", "地点"],
            ["area", "エリア"],
            ["unknown", "不明"],
          ] as const).map(([value, label]) => (
            <ChipButton key={value} active={kind === value} onClick={() => selectKind(value)}>
              {label}
            </ChipButton>
          ))}
        </div>
      </div>

      {kind !== "unknown" ? (
        <>
          <div className="mt-4 flex flex-col gap-1.5">
            <span className="text-xs font-medium text-muted-foreground">入力手段</span>
            <div className="flex flex-wrap gap-2">
              {([
                ["current", "現在地"],
                ["map", "地図"],
                ["search", "検索"],
              ] as const).map(([value, label]) => (
                <ChipButton key={value} active={method === value} onClick={() => setMethod(value)}>
                  {label}
                </ChipButton>
              ))}
            </div>
          </div>

          {method === "current" ? (
            <div className="mt-3 flex flex-col gap-2 text-sm">
              <p className="text-xs text-muted-foreground">
                端末の位置情報の利用許可を求めます（モック）。取得できない場合は地図・検索に切り替えてください。
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="w-fit"
                onClick={() => setPicked({ name: "現在地（モック）", lat: 35.0, lng: 139.0 })}
              >
                現在地を取得する（モック）
              </Button>
            </div>
          ) : null}

          {method === "map" ? (
            <button
              type="button"
              onClick={() => setPicked({ name: "地図上の地点（モック）", lat: 35.3, lng: 139.4 })}
              className="mt-3 flex h-24 w-full items-center justify-center rounded border border-dashed text-xs text-muted-foreground hover:bg-accent"
            >
              地図（モック）。クリックしてピンを立てる
            </button>
          ) : null}

          {method === "search" ? (
            <div className="mt-3 flex flex-col gap-2">
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="住所・施設名・駅名等"
                className="h-9 rounded-md border border-input bg-background px-3 text-sm"
              />
              <div className="flex flex-col gap-1">
                {results.map((r) => (
                  <button
                    key={r.name}
                    type="button"
                    onClick={() => setPicked(r)}
                    className={cnResult(picked?.name === r.name)}
                  >
                    {r.name}（{r.address}）
                  </button>
                ))}
                {results.length === 0 ? <p className="text-xs text-muted-foreground">候補が見つかりません</p> : null}
              </div>
            </div>
          ) : null}

          {picked ? (
            <p className="mt-3 text-sm">
              選択中: <span className="font-medium">{picked.name}</span>
              {picked.address ? `（${picked.address}）` : ""}
            </p>
          ) : null}

          {kind === "area" ? (
            <div className="mt-4 flex flex-col gap-2">
              <span className="text-xs font-medium text-muted-foreground">エリアの指定方法</span>
              <div className="flex flex-wrap gap-2">
                {([
                  ["radius", "中心地点からの周辺範囲"],
                  ["named", "名前付き地域"],
                ] as const).map(([value, label]) => (
                  <ChipButton key={value} active={areaType === value} onClick={() => setAreaType(value)}>
                    {label}
                  </ChipButton>
                ))}
              </div>
              {areaType === "radius" ? (
                <label className="flex items-center gap-2 text-sm">
                  周辺範囲
                  <input
                    type="number"
                    value={radius}
                    onChange={(event) => setRadius(event.target.value)}
                    className="h-9 w-28 rounded-md border border-input bg-background px-3 text-sm"
                  />
                  m
                </label>
              ) : (
                <input
                  value={namedArea}
                  onChange={(event) => setNamedArea(event.target.value)}
                  placeholder="市区町村・町名等"
                  className="h-9 rounded-md border border-input bg-background px-3 text-sm"
                />
              )}
            </div>
          ) : null}
        </>
      ) : (
        <p className="mt-3 text-xs text-muted-foreground">
          場所が分からないことを明示します。未入力(未設定)とは区別されます。
        </p>
      )}

      <div className="mt-6 flex flex-wrap justify-end gap-2 border-t pt-4">
        <Button type="button" variant="ghost" size="sm" onClick={onUnset}>
          未指定にする
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onClose}>
          閉じる
        </Button>
        <Button
          type="button"
          size="sm"
          onClick={handleSubmit}
          disabled={kind === "point" && !picked?.name}
        >
          場所を設定
        </Button>
      </div>
    </Dialog>
  );
}

function ChipButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={
        active
          ? "rounded-full border border-foreground bg-foreground px-3 py-1 text-xs font-medium text-background"
          : "rounded-full border border-input px-3 py-1 text-xs font-medium hover:bg-accent"
      }
    >
      {children}
    </button>
  );
}

function cnResult(selected: boolean) {
  return selected
    ? "rounded border border-foreground bg-accent px-3 py-2 text-left text-sm"
    : "rounded border border-input px-3 py-2 text-left text-sm hover:bg-accent";
}
