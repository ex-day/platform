// see docs/ui/screens/S08-user-edit.md「ニックネームの入力のルール」(Issue #74)
//
// ニックネームの入力チェック。S08(ユーザー情報更新)の「更新」押下時に使う。
// 初回登録のS07(#75の決定後に定義)でも同じルールを使う想定。

export const NICKNAME_MAX_WIDTH = 24;

export const NICKNAME_BANNED_WORDS = ["ex-day", "運営", "公式"] as const;

export type NicknameError = "empty" | "invalidChar" | "tooLong" | "banned";

export const NICKNAME_ERROR_MESSAGES: Record<NicknameError, string> = {
  empty: "ニックネームを入力してください。",
  invalidChar: "使えない文字（改行・制御文字）が含まれています。",
  tooLong: "全角12文字（半角24文字）相当までです。",
  banned: "「ex-day」「運営」「公式」を含む名前は使えません。",
};

const segmenter = new Intl.Segmenter("ja", { granularity: "grapheme" });

const EMOJI_PATTERN = /\p{Extended_Pictographic}|\p{Regional_Indicator}/u;
// 半角＝1と数える文字：ASCIIの印字可能文字と半角カナ(U+FF61〜U+FF9F)
const HALF_WIDTH_PATTERN = /^[ -~｡-ﾟ]$/u;

/** 前後の空白を削る(全角スペースを含む) */
export function normalizeNickname(value: string): string {
  return value.trim();
}

/** 文字数(全角＝2、半角＝1、絵文字＝2)。絵文字は結合された1つの見た目を1文字として数える */
export function nicknameWidth(value: string): number {
  let width = 0;
  for (const { segment } of segmenter.segment(value)) {
    if (EMOJI_PATTERN.test(segment)) width += 2;
    else if (HALF_WIDTH_PATTERN.test(segment)) width += 1;
    else width += 2;
  }
  return width;
}

/**
 * 禁止語の照合用に表記の揺れをそろえる(案。そろえ方の範囲はIssue #79に記録する)。
 * - NFKCで全角英数字・記号を半角へ(「ｅｘ－ｄａｙ」→「ex-day」)
 * - 小文字へ
 * - ハイフン・ダッシュ類、長音記号に似た横棒、空白、中黒、ピリオド、アンダースコアを取り除く
 */
export function foldForBannedWords(value: string): string {
  return value
    .normalize("NFKC")
    .toLowerCase()
    .replace(/[\s\-‐‑‒–—―−_・.･]/gu, "");
}

const FOLDED_BANNED_WORDS = NICKNAME_BANNED_WORDS.map(foldForBannedWords);

export function containsBannedWord(value: string): boolean {
  const folded = foldForBannedWords(value);
  return FOLDED_BANNED_WORDS.some((word) => folded.includes(word));
}

/** 改行・制御文字(行区切り・段落区切りを含む) */
const INVALID_CHAR_PATTERN = /[\p{Cc}\u2028\u2029]/u;

/**
 * 前後の空白を削った値をチェックし、最初に当たったエラーを返す。問題がなければnull。
 * 重複の確認はしない(他の人と同じニックネームでもよい)。「ななしさん」も使える。
 */
export function validateNickname(value: string): NicknameError | null {
  const normalized = normalizeNickname(value);
  if (normalized.length === 0) return "empty";
  if (INVALID_CHAR_PATTERN.test(normalized)) return "invalidChar";
  if (nicknameWidth(normalized) > NICKNAME_MAX_WIDTH) return "tooLong";
  if (containsBannedWord(normalized)) return "banned";
  return null;
}
