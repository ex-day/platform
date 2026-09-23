// see docs/ui/screens/S04-contribution-save.md, docs/ui/components/C22-location-input.md,
// C23-media-source-dialog.md, C24-time-period-input.md, DEC-0002, DEC-0003
//
// S04モック用の編集中Contribution(Draft)の型と、編集モード用の初期値。
// API未接続のため、ここでの型・項目名は実際のAPI契約を先取りするものではない
// (S04-contribution-save.md「表示項目の物理名・データ項目はUI上の識別用」を参照)。

export type ContributionKind = "knowledge" | "question";

export type MediaKind = "image" | "video" | "pdf" | "other";

export type MediaSourceStatus = "unset" | "self" | "registered" | "unknown";

export type MediaSourceType = "website" | "book" | "document" | "provided" | "other";

export type DraftMedia = {
  id: string;
  name: string;
  kind: MediaKind;
  /** ローカルファイルのプレビュー用URL(URL.createObjectURL)。モックのため永続化しない */
  previewUrl?: string;
  sourceStatus: MediaSourceStatus;
  sourceType?: MediaSourceType;
  sourceName?: string;
  sourceUrl?: string;
  sourceNote?: string;
  createdAt?: string;
  targetTime?: string;
  /**
   * 不適切資料の簡易判定(モック上の仮ルール)。ファイル名に"ng"を含む場合に
   * 検証用として不適切とみなす。実際の検出方式はIssue #35で確定する
   * (docs/ui/screens/S04-contribution-save.md 検討事項)。
   */
  flaggedInappropriate: boolean;
};

export type LocationValue =
  | { kind: "unknown" }
  | {
      kind: "point";
      name: string;
      address?: string;
      lat?: number;
      lng?: number;
    }
  | {
      kind: "area";
      name?: string;
      areaType: "radius" | "named";
      lat?: number;
      lng?: number;
      radius?: number;
      namedArea?: string;
    };

export type TimeAnswer = "current" | "past" | "future" | "unknown";

export type TimePeriodValue = {
  periodType?: "approximate" | "specific";
  approximatePeriod?: string;
  specificPeriod?: string;
  timeFrom?: string;
  timeTo?: string;
  text?: string;
};

export type ContributionDraft = {
  mode: "new" | "edit";
  editId?: string;
  contributionType: ContributionKind;
  originDiscovery?: { id: string; title: string };
  body: string;
  media: DraftMedia[];
  /** 未設定(未入力)はnull。C22で明示的に「不明」を選ぶとkind:"unknown"になる */
  place: LocationValue | null;
  timeAnswer: TimeAnswer | null;
  timeDetail: string;
  seasons: string[];
  timesOfDay: string[];
  /** C24で設定した詳細。未設定はnull */
  timePeriod: TimePeriodValue | null;
};

/**
 * S03からDiscovery IDを指定して遷移した場合の仮の遷移元Discovery。
 * 実際のS03→S04導線・パラメータ形式はIssue #39で確定する。
 */
export const ORIGIN_DISCOVERY_PRESETS: Record<string, { id: string; title: string; place: LocationValue }> = {
  "sample-1": {
    id: "sample-1",
    title: "小机城址の夕景スポット",
    place: { kind: "point", name: "小机城址", address: "横浜市港北区" },
  },
};

export function createEmptyDraft(originDiscoveryId?: string): ContributionDraft {
  const origin = originDiscoveryId ? ORIGIN_DISCOVERY_PRESETS[originDiscoveryId] : undefined;
  return {
    mode: "new",
    contributionType: "knowledge",
    originDiscovery: origin ? { id: origin.id, title: origin.title } : undefined,
    body: "",
    media: [],
    // 遷移元Discoveryが場所を持つ場合、その場所をC22の初期入力とする(固定はしない)
    place: origin ? origin.place : null,
    timeAnswer: null,
    timeDetail: "",
    seasons: [],
    timesOfDay: [],
    timePeriod: null,
  };
}

/**
 * 編集モードの初期値。WireframeのPC-S04-Edit-Knowledge.pngに合わせたsample-1と、
 * 未確認事項を確認しやすいよう任意詳細が未入力のケースを別途用意する。
 */
export function getEditableDraft(id: string): ContributionDraft {
  if (id === "sample-2") {
    return {
      mode: "edit",
      editId: id,
      contributionType: "question",
      body: "この石碑、何のためにあるんだろう？由来を知っている人いますか。",
      media: [],
      place: null,
      timeAnswer: null,
      timeDetail: "",
      seasons: [],
      timesOfDay: [],
      timePeriod: null,
    };
  }

  return {
    mode: "edit",
    editId: id,
    contributionType: "knowledge",
    body: "小机城址は今では公園として整備されていて、休日には近所の人たちがのんびり散歩している。中世には後北条氏の支城として使われていたらしい。",
    media: [
      {
        id: "media-1",
        name: "写真1.jpg",
        kind: "image",
        sourceStatus: "self",
        flaggedInappropriate: false,
      },
    ],
    place: { kind: "point", name: "小机城址", address: "横浜市港北区" },
    timeAnswer: "past",
    timeDetail: "中世（戦国期）ごろ",
    seasons: ["all-year"],
    timesOfDay: [],
    timePeriod: null,
  };
}

const HANDOFF_PREFIX = "exday-s04-handoff:";

/**
 * DEC-0003「受け取って解析する」方式のモック実装。S04はS11の解析完了を待たず、
 * 入力内容をsessionStorageへ保存してS11へ即時遷移する。バックエンドを持たない
 * モックのため、実際のAPI呼び出し・永続化を代替するものではない。
 */
export function saveDraftForHandoff(draft: ContributionDraft): string {
  const handoffId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  try {
    sessionStorage.setItem(`${HANDOFF_PREFIX}${handoffId}`, JSON.stringify(draft));
  } catch {
    // sessionStorageが使えない環境向けのフォールバックは行わない(モックの範囲外)
  }
  return handoffId;
}

export function readHandoffDraft(handoffId: string): ContributionDraft | null {
  try {
    const raw = sessionStorage.getItem(`${HANDOFF_PREFIX}${handoffId}`);
    return raw ? (JSON.parse(raw) as ContributionDraft) : null;
  } catch {
    return null;
  }
}

export const SEASON_OPTIONS: { value: string; label: string }[] = [
  { value: "all-year", label: "通年" },
  { value: "spring", label: "春" },
  { value: "summer", label: "夏" },
  { value: "autumn", label: "秋" },
  { value: "winter", label: "冬" },
  { value: "unknown", label: "不明" },
];

export const TIME_OF_DAY_OPTIONS: { value: string; label: string }[] = [
  { value: "morning", label: "朝" },
  { value: "noon", label: "昼" },
  { value: "evening", label: "夕方" },
  { value: "night", label: "夜" },
  { value: "anytime", label: "いつでも" },
  { value: "unknown", label: "不明" },
];

export const TIME_ANSWER_OPTIONS: { value: TimeAnswer; label: string }[] = [
  { value: "current", label: "現在" },
  { value: "past", label: "過去" },
  { value: "future", label: "未来" },
  { value: "unknown", label: "不明" },
];

/** 検索可能なサジェスト候補(モック用の固定候補)。候補が無くても自由入力できる */
export const TIME_DETAIL_SUGGESTIONS = [
  "中世",
  "戦国時代",
  "江戸時代",
  "明治時代",
  "昭和30年代",
  "1600年頃",
];

/** 検証用の禁止ワード(モック用の仮リスト。最終的な語彙・判定方式は未確定) */
export const MOCK_FORBIDDEN_WORDS = ["死ね", "スパムテスト"];

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

/**
 * 選択されたファイルを出典状態="unset"の資料に変換する。S04(ContributionEditor)と
 * S03のコメント(C26)で共用し、添付時点で出典登録を求めない(C23「呼び出しのタイミング」)。
 * ブラウザ上でのみ呼び出す(URL.createObjectURLを使用)。
 */
export function filesToDraftMedia(files: FileList): DraftMedia[] {
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

export function describeSourceStatus(status: MediaSourceStatus): string {
  switch (status) {
    case "self":
      return "自分で撮影・作成";
    case "registered":
      return "出典登録済み";
    case "unknown":
      return "出典不明";
    default:
      return "未設定";
  }
}

/** C22の表示・S11(Mock)の受け取り内容表示で共有する場所の要約表示 */
export function describeLocation(value: LocationValue | null): string {
  if (!value) return "場所を教えてください";
  if (value.kind === "unknown") return "場所：不明";
  if (value.kind === "point") return `${value.name}${value.address ? `（${value.address}）` : ""}`;
  if (value.areaType === "named") return `${value.namedArea ?? "名前付き地域"}周辺`;
  return `${value.name ?? "中心地点"}から${value.radius ?? "?"}m圏内`;
}

/** C24の表示・S11(Mock)の受け取り内容表示で共有する時期・期間の要約表示 */
export function describeTimePeriod(value: TimePeriodValue | null): string {
  if (!value) return "未設定";
  const parts: string[] = [];
  if (value.periodType === "approximate" && value.approximatePeriod) parts.push(value.approximatePeriod);
  if (value.periodType === "specific" && value.specificPeriod) parts.push(value.specificPeriod);
  if (value.timeFrom || value.timeTo) parts.push(`${value.timeFrom ?? "?"}〜${value.timeTo ?? "?"}`);
  if (value.text) parts.push(value.text);
  return parts.length > 0 ? parts.join(" / ") : "未設定";
}
