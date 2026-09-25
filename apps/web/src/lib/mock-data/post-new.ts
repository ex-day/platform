// see docs/ui/components/C27-post-new.md, docs/ui/functions/F03-similar-discovery-guide.md
//
// C27(新しい話を始める)の投稿直後に示す、似たDiscoveryとわかってきたことのモック。
// 実際の検索(F03)は行わず、固定のデータで表示を確認する。0件も正常とする(DEC-0010 決定1)。

export type SimilarDiscovery = {
  id: string;
  title: string;
  /** 似たDiscoveryのわかってきたこと(説・価値)。説は正解のように見せない */
  findings: { kind: "value" | "theory"; text: string }[];
};

export const MOCK_SIMILAR_DISCOVERIES: SimilarDiscovery[] = [
  {
    id: "sample-1",
    title: "○○城跡でみつけた、静かな夕景",
    findings: [
      { kind: "value", text: "夕方、石垣の上から見る夕景が綺麗" },
      { kind: "theory", text: "戦国期には後北条氏の支城だったのでは" },
    ],
  },
  {
    id: "exploring",
    title: "鶴見の浜で干していた貝は何？",
    findings: [
      { kind: "theory", text: "昭和30年代、鶴見の浜では青柳（バカガイ）を干していた（裏付け：鶴見区史）" },
      { kind: "value", text: "昭和の浜の暮らしを、古写真でたどれる" },
    ],
  },
];

/** 投稿後に器として作られた成立前Discoveryのモック上の遷移先 */
export const MOCK_NEW_DISCOVERY_ID = "exploring-start";
