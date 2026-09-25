// see docs/ui/components/C23-media-source-dialog.md, C26-post-thread.md, C27-post-new.md, DEC-0005 決定8
//
// 声(Post)とともにDiscoveryへ提供される資料のモック用の型と補助関数。
// 旧S04(知識・疑問登録／編集)用のcontribution-draft.tsから、資料に関する部分だけを残した(Issue #67)。
// API未接続のため、ここでの型・項目名は実際のAPI契約を先取りするものではない。

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
   * (docs/ui/components/C27-post-new.md 検討事項)。
   */
  flaggedInappropriate: boolean;
};

function kindFromMime(mime: string): MediaKind {
  if (mime.startsWith("image/")) return "image";
  if (mime.startsWith("video/")) return "video";
  if (mime === "application/pdf") return "pdf";
  return "other";
}

/**
 * 不適切資料の簡易判定(モック上の仮ルール)。ファイル名(拡張子除く)が単語として
 * "ng"を含む場合に検証用として不適切とみなす。実際の検出方式は
 * Issue #35で確定する。
 */
function isFlaggedName(fileName: string): boolean {
  const stem = fileName.replace(/\.[^./]+$/, "");
  return /\bng\b/i.test(stem);
}

/**
 * 選択されたファイルを出典状態="unset"の資料に変換する。C26(みんなの声)と
 * C27(新しい話を始める)で共用し、添付時点で出典登録を求めない(C23「呼び出しのタイミング」)。
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
