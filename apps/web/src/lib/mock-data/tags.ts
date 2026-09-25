// see docs/ui/functions/F04-tag-suggest.md
//
// F04(タグの補完)のモック。ex-dayに保管されているタグとの照合だけで候補を返し、
// AIは使わない(Issue #67「判断：タグのMVPでの扱い」)。タグの一覧と使用数は仮置きの固定値。

export type StoredTag = { name: string; count: number };

export const STORED_TAGS: StoredTag[] = [
  { name: "城跡", count: 128 },
  { name: "夕景", count: 96 },
  { name: "桜", count: 240 },
  { name: "石垣", count: 44 },
  { name: "紅葉", count: 180 },
  { name: "古写真", count: 72 },
  { name: "鶴見", count: 35 },
  { name: "青柳干し", count: 12 },
  { name: "干し場", count: 9 },
  { name: "浜松町", count: 28 },
  { name: "ビルの入口", count: 6 },
  { name: "水害", count: 31 },
  { name: "キッチンカー", count: 58 },
  { name: "石碑", count: 22 },
  { name: "登山道", count: 64 },
  { name: "汽車道", count: 40 },
  { name: "貨物線", count: 18 },
];

const byCount = (a: StoredTag, b: StoredTag) => b.count - a.count;

/**
 * #タグの判定。「#」に続く、空白・「#」・句読点・括弧以外の文字の並びをタグとする。
 * 入力欄の強調(TagAwareTextarea)・投稿後の表示(BodyWithTags)・タグの取り出しで共有する
 * (入力したときの見た目と投稿後の見た目をそろえる。Issue #67「判断：タグの入力と表示」)。
 * 区切りの規則はF04の検討事項。
 */
const TAG_CHARS = "[^\\s#、。,.!?！？「」()（）]";
const TAG_PATTERN = new RegExp(`#${TAG_CHARS}+`, "g");
const ACTIVE_HASH_PATTERN = new RegExp(`#(${TAG_CHARS}*)$`);

export type TextSegment = { text: string; isTag: boolean };

/** 本文を#タグとそれ以外に分ける(つなげると元の本文に戻る) */
export function splitByTags(text: string): TextSegment[] {
  const segments: TextSegment[] = [];
  let last = 0;
  for (const m of text.matchAll(TAG_PATTERN)) {
    const index = m.index ?? 0;
    if (index > last) segments.push({ text: text.slice(last, index), isTag: false });
    segments.push({ text: m[0], isTag: true });
    last = index + m[0].length;
  }
  if (last < text.length) segments.push({ text: text.slice(last), isTag: false });
  return segments;
}

/** 本文中の「#」で書かれたタグを取り出す(書いたまま) */
export function extractTags(text: string): string[] {
  const found = text.match(TAG_PATTERN) ?? [];
  return Array.from(new Set(found.map((t) => t.slice(1))));
}

/** カーソル直前の「#xxx」を返す。入力中でなければnull */
export function activeHashQuery(text: string, caret: number): { start: number; query: string } | null {
  const before = text.slice(0, caret);
  const m = before.match(ACTIVE_HASH_PATTERN);
  if (!m) return null;
  return { start: caret - m[0].length, query: m[1] };
}

/** 「#」のあとの補完。前方一致(空なら全件)を、よく使われている順に返す */
export function suggestTags(query: string, limit = 6): StoredTag[] {
  return STORED_TAGS.filter((t) => t.name.startsWith(query)).sort(byCount).slice(0, limit);
}

/** タグで見せる単位はDiscoveryとし、タグを条件にS02へ遷移する(遷移先は案。Issue #67に判断待ちとして記録) */
export function tagSearchHref(tag: string) {
  return `/discoveries?entry=search&q=${encodeURIComponent(`#${tag}`)}`;
}
