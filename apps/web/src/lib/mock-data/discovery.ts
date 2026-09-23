// see docs/ui/screens/S03-discovery-detail.md
//
// S03モック用のダミーデータ。API未接続の段階であり、ここでの型・項目名は
// 実際のAPI契約を先取りするものではない(docs/ui/components/C19-discovery-detail-hero.md
// 「表示に必要な文言・Mediaを用意する担当と具体的なデータ契約は未確定」を参照)。

import type { DraftMedia } from "@/lib/mock-data/contribution-draft";

export type DiscoveryCondition = {
  label: string;
  value: string;
};

export type DiscoveryValueItem = {
  id: string;
  subject: string;
  value: string;
  /** 実画像は用いず、モックでは説明キャプションのプレースホルダーとして扱う */
  picture?: string;
  body: string;
  conditions: DiscoveryCondition[];
};

export type DiscoveryRecommendation = {
  id: string;
  message: string;
  participationMessage?: string;
  media?: { alt: string };
};

export type DiscoveryPlace = {
  text: string;
  lat: number;
  lng: number;
};

export type RelatedDiscoverySummary = {
  id: string;
  subject: string;
  value: string;
  title: string;
  picture?: string;
  place: DiscoveryPlace;
};

/**
 * S03に埋め込むコメント(C26)。DEC-0005によりContributionを発言(コメント)として扱う。
 * 添付資料はS04と同じDraftMedia型で持ち、出典状態は"unset"を許容する(C23)。
 */
export type DiscoveryComment = {
  id: string;
  author: string;
  /** 相対表現の表示用文字列(モックのため日時計算はしない) */
  postedAtLabel: string;
  body: string;
  media: DraftMedia[];
};

export type Discovery = {
  id: string;
  title: string;
  body: string;
  place: DiscoveryPlace;
  recommendations: DiscoveryRecommendation[];
  otherValues: DiscoveryValueItem[];
  reactionCount: number;
  /** C25。複数のコメントから抽出された未確定の語句 */
  emergingTerms: string[];
  /** C26。時系列(古い→新しい)順 */
  comments: DiscoveryComment[];
  derivedFrom: RelatedDiscoverySummary[];
  derivedTo: RelatedDiscoverySummary[];
  related: RelatedDiscoverySummary[];
};

function mockMedia(
  id: string,
  name: string,
  source: Pick<DraftMedia, "sourceStatus" | "sourceType" | "sourceName"> = { sourceStatus: "unset" },
): DraftMedia {
  return { id, name, kind: "image", flaggedInappropriate: false, ...source };
}

function relatedItems(
  prefix: string,
  count: number,
  base: Omit<RelatedDiscoverySummary, "id" | "title">,
): RelatedDiscoverySummary[] {
  return Array.from({ length: count }, (_, i) => ({
    ...base,
    id: `${prefix}-${i + 1}`,
    title: `${base.subject}にまつわる発見 ${i + 1}`,
  }));
}

const DISCOVERIES: Record<string, Discovery> = {
  "sample-1": {
    id: "sample-1",
    title: "○○城跡でみつけた、静かな夕景",
    body: "石垣の上から見渡す夕景は、観光地図には載っていない静けさがある。\n春は桜、秋は紅葉と、季節ごとに異なる魅力を持つ場所。",
    place: { text: "○○県○○市", lat: 35.1815, lng: 136.9066 },
    recommendations: [
      {
        id: "rec-1",
        message: "今、桜が見頃です",
        media: { alt: "夕暮れの城跡と満開の桜" },
      },
      {
        id: "rec-2",
        message: "紅葉スポットとしても知られています",
        media: { alt: "紅葉に染まる石垣" },
      },
    ],
    otherValues: [
      {
        id: "value-1",
        subject: "石垣",
        value: "夜はライトアップされ、幻想的な雰囲気になる",
        picture: "ライトアップされた石垣",
        body: "日没後30分ほどでライトアップが始まる。三脚を使った撮影も可能。",
        conditions: [
          { label: "時間", value: "日没後〜21:00" },
          { label: "季節", value: "通年" },
        ],
      },
      {
        id: "value-2",
        subject: "散策路",
        value: "本丸まで15分ほどの散策路がある",
        body: "舗装されており歩きやすいが、一部急な階段がある。",
        conditions: [{ label: "季節", value: "積雪期を除く" }],
      },
    ],
    reactionCount: 128,
    emergingTerms: ["後北条氏の支城", "戦国期", "夕景", "桜の名所", "石垣の積み方"],
    comments: [
      {
        id: "comment-1",
        author: "くみ子",
        postedAtLabel: "12日前",
        body: "子どもの頃、祖父とよくここから夕日を見ていました。当時の写真が出てきたので載せます。",
        media: [mockMedia("comment-1-media-1", "昭和50年代の城跡.jpg")],
      },
      {
        id: "comment-2",
        author: "みつる",
        postedAtLabel: "10日前",
        body: "戦国期には後北条氏の支城だったと聞いたことがあります。",
        media: [],
      },
      {
        id: "comment-3",
        author: "はるか",
        postedAtLabel: "6日前",
        body: "市の資料館に古い縄張り図がありました。石垣の積み方も時代で違うみたいです。",
        media: [
          mockMedia("comment-3-media-1", "縄張り図.jpg", {
            sourceStatus: "registered",
            sourceType: "document",
            sourceName: "○○市郷土資料館 所蔵資料",
          }),
        ],
      },
      {
        id: "comment-4",
        author: "けんじ",
        postedAtLabel: "2日前",
        body: "今週末あたり桜が見頃になりそうです。夕方の石垣と一緒に撮ってきました。",
        media: [mockMedia("comment-4-media-1", "夕景と桜.jpg", { sourceStatus: "self" })],
      },
      {
        id: "comment-5",
        author: "ゆかり",
        postedAtLabel: "3時間前",
        body: "くみ子さんの写真、今と見比べると石垣の上の木がずいぶん育っていますね。",
        media: [],
      },
    ],
    derivedFrom: relatedItems("derived-from", 1, {
      subject: "城下町の街並み",
      value: "城跡の麓に広がる古い街並み",
      place: { text: "○○県○○市", lat: 35.183, lng: 136.905 },
    }),
    derivedTo: relatedItems("derived-to", 8, {
      subject: "夜桜",
      value: "城跡から派生した夜桜スポットの発見",
      place: { text: "○○県○○市", lat: 35.18, lng: 136.907 },
    }),
    related: relatedItems("related", 4, {
      subject: "同じ藩の史跡",
      value: "同じ藩に関連する史跡",
      place: { text: "○○県△△市", lat: 35.2, lng: 136.95 },
    }),
  },
  "sample-2": {
    id: "sample-2",
    title: "△△山の謎の石碑について",
    body: "登山道の途中に、由来の分からない石碑がある。\n地元の人に聞いても詳しいことは分からなかった。",
    place: { text: "△△県△△町", lat: 36.05, lng: 137.6 },
    recommendations: [
      {
        id: "rec-1",
        message: "この場所について疑問が寄せられています",
        participationMessage: "一緒に考えませんか？",
      },
    ],
    otherValues: [],
    reactionCount: 12,
    emergingTerms: ["道標？", "供養塔？", "江戸時代"],
    comments: [
      {
        id: "comment-1",
        author: "たけし",
        postedAtLabel: "4日前",
        body: "形からすると道標か供養塔かもしれません。裏側の文字を撮ってきました。",
        media: [mockMedia("comment-1-media-1", "石碑の裏側.jpg")],
      },
      {
        id: "comment-2",
        author: "はるか",
        postedAtLabel: "1日前",
        body: "年号が読めれば江戸時代のものか分かりそうですね。",
        media: [],
      },
    ],
    derivedFrom: [],
    derivedTo: relatedItems("derived-to", 1, {
      subject: "登山道の分岐点",
      value: "石碑のある分岐点から派生した発見",
      place: { text: "△△県△△町", lat: 36.052, lng: 137.602 },
    }),
    related: relatedItems("related", 2, {
      subject: "近隣の登山道",
      value: "近隣の登山道に関する発見",
      place: { text: "△△県△△町", lat: 36.06, lng: 137.61 },
    }),
  },
  "sample-3": {
    id: "sample-3",
    title: "□□川沿いの遊歩道",
    body: "まだ評価済みのおすすめが登録されていない発見。\nHeroの表示有無は検討事項のため、モックでは非表示としている。",
    place: { text: "□□県□□市", lat: 34.7, lng: 135.5 },
    recommendations: [],
    otherValues: [
      {
        id: "value-1",
        subject: "遊歩道",
        value: "川沿いを1周できる遊歩道がある",
        body: "1周約3km、平坦で歩きやすい。",
        conditions: [{ label: "季節", value: "通年" }],
      },
      {
        id: "value-2",
        subject: "野鳥観察",
        value: "早朝は野鳥観察に適している",
        body: "双眼鏡があるとより楽しめる。",
        conditions: [{ label: "時間", value: "早朝" }],
      },
      {
        id: "value-3",
        subject: "休憩スポット",
        value: "中間地点にベンチと東屋がある",
        body: "屋根付きのため小雨でも休憩できる。",
        conditions: [],
      },
    ],
    reactionCount: 3,
    emergingTerms: [],
    comments: [],
    derivedFrom: relatedItems("derived-from", 3, {
      subject: "川の上流",
      value: "上流にある発見",
      place: { text: "□□県□□市", lat: 34.71, lng: 135.51 },
    }),
    derivedTo: [],
    related: relatedItems("related", 7, {
      subject: "近隣の公園",
      value: "近隣の公園に関する発見",
      place: { text: "□□県□□市", lat: 34.69, lng: 135.49 },
    }),
  },
};

export function getDiscoveryById(id: string): Discovery {
  return DISCOVERIES[id] ?? { ...DISCOVERIES["sample-1"], id };
}
