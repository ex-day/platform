// see docs/ui/screens/S08-user-edit.md(興味のある地域)
//
// 興味のある地域の補完に使うダミーの地域データ。市区町村程度を基本とする。
// 補完の元データ・粒度は検討事項(市区町村より広い・狭い地域の扱いを含む)のため、
// 比較用に「港北ニュータウン」のような市区町村をまたぐ地域も1件置いている。

import type { InterestArea } from "@/lib/mock-data/user";

export type AreaCandidate = InterestArea & {
  /** 読み(ひらがな)。入力に合わせた補完に使う */
  kana: string;
  /** 補足(都道府県等) */
  note: string;
};

export const MOCK_AREAS: AreaCandidate[] = [
  { id: "yokohama-kohoku", name: "横浜市港北区", kana: "よこはましこうほくく", note: "神奈川県" },
  { id: "kohoku-newtown", name: "港北ニュータウン", kana: "こうほくにゅーたうん", note: "横浜市都筑区ほか" },
  { id: "yokohama-naka", name: "横浜市中区", kana: "よこはましなかく", note: "神奈川県" },
  { id: "yokohama-tsurumi", name: "横浜市鶴見区", kana: "よこはましつるみく", note: "神奈川県" },
  { id: "kamakura", name: "鎌倉市", kana: "かまくらし", note: "神奈川県" },
  { id: "odawara", name: "小田原市", kana: "おだわらし", note: "神奈川県" },
  { id: "tokyo-minato", name: "港区", kana: "みなとく", note: "東京都" },
  { id: "tokyo-taito", name: "台東区", kana: "たいとうく", note: "東京都" },
  { id: "kyoto-higashiyama", name: "京都市東山区", kana: "きょうとしひがしやまく", note: "京都府" },
  { id: "kyoto-sakyo", name: "京都市左京区", kana: "きょうとしさきょうく", note: "京都府" },
  { id: "nara", name: "奈良市", kana: "ならし", note: "奈良県" },
  { id: "kanazawa", name: "金沢市", kana: "かなざわし", note: "石川県" },
  { id: "matsumoto", name: "松本市", kana: "まつもとし", note: "長野県" },
  { id: "hakodate", name: "函館市", kana: "はこだてし", note: "北海道" },
  { id: "onomichi", name: "尾道市", kana: "おのみちし", note: "広島県" },
];

function toHiragana(value: string) {
  return value.replace(/[ァ-ヶ]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0x60));
}

/** 名前・読み・補足の部分一致で補完する(モック。実際の照合方法は元データとあわせて検討する) */
export function suggestAreas(query: string, excludeIds: string[] = [], limit = 5): AreaCandidate[] {
  const q = toHiragana(query.trim().normalize("NFKC"));
  if (!q) return [];
  return MOCK_AREAS.filter(
    (a) =>
      !excludeIds.includes(a.id) &&
      (a.name.includes(q) || a.kana.includes(q) || a.note.includes(q)),
  ).slice(0, limit);
}
