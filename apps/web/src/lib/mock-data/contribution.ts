// see docs/ui/screens/S05-contribution-detail.md
//
// S05モック用のダミーデータ。API未接続の段階であり、ここでの型・項目名は
// 実際のAPI契約を先取りするものではない(docs/ui/components/C20-discovery-impact.md
// 「Discovery単位のImpactを取得する具体的なAPI契約は...API設計で定める」を参照)。

import type { RelatedDiscoverySummary } from "./discovery";

export type ContributionType = "knowledge" | "question";

export type ContributionPicture = {
  /** 実画像は用いず、モックでは説明キャプションのプレースホルダーとして扱う */
  caption: string;
};

export type ContributionAttachment = {
  name: string;
};

/**
 * C20 discovery-impact 1件分。1 Contribution × 1 Discoveryの組み合わせを表す。
 * providedValues / discoveryFormationImpacts / valuePendingは
 * すべて0..n・0..1であり、併存し得る(Non-blocking #1: 特定済みValueと
 * Value未特定の寄与が同じDiscoveryに併存する場合の表示は未定義のため、
 * モックでは両方を並べる)。
 */
export type DiscoveryImpact = {
  discoveryId: string;
  discoveryTitle: string;
  discoverySubject: string;
  discoveryPlace: string;
  /** Discovery形成に利用された場合の結果文言。0..n */
  formationImpacts: string[];
  /** 提供したValueごとの利用結果。0..n */
  providedValues: { valueName: string; impact: string }[];
  /** Discoveryにつながった人数。0..1 */
  reach?: { count: number; period: string };
  /** 対象Valueが未成立・未特定の場合の案内。0..1 */
  valuePending?: string;
};

export type Contribution = {
  id: string;
  type: ContributionType;
  body: string;
  pictures: ContributionPicture[];
  attachment?: ContributionAttachment;
  place?: string;
  time?: string;
  season?: string;
  postedAt: string;
  author: string;
  reactionCount: number;
  /** 関係が確定したDiscoveryのみ。未確定の場合は空配列(C15非表示) */
  relatedDiscoveries: RelatedDiscoverySummary[];
  /** 投稿者本人か(自分の確認コンテキストでのみ意味を持つ) */
  isOwnerViewing: boolean;
  status: {
    /** 例:「Discoveryとつながっています」「Discoveryはまだ決まっていません」 */
    label: string;
    confirmedContext?: string;
    subject?: string;
  };
  /** 確認が必要な項目。空配列ならC18は非表示 */
  confirmationRequests: string[];
  /** C17。空配列なら「現在確認中です」を表示 */
  discoveryImpacts: DiscoveryImpact[];
};

const CONTRIBUTIONS: Record<string, Contribution> = {
  "sample-1": {
    id: "sample-1",
    type: "knowledge",
    body: "今日夕方○○公園に行ったら夕焼けが綺麗だったよー。写真も撮ってきた。",
    pictures: [
      { caption: "夕焼けの全景" },
      { caption: "公園入口" },
      { caption: "ベンチからの眺め" },
    ],
    attachment: { name: "撮影メモ.pdf" },
    place: "○○公園付近",
    time: "夕方",
    season: "秋",
    postedAt: "2026-09-20 12:30",
    author: "ユーザーA",
    reactionCount: 8,
    isOwnerViewing: true,
    relatedDiscoveries: [
      {
        id: "sample-1",
        subject: "夕景",
        value: "夕焼けが綺麗",
        title: "○○公園の夕焼けスポット",
        place: { text: "○○県○○市", lat: 35.1, lng: 136.9 },
      },
      {
        id: "sample-2",
        subject: "散策",
        value: "人が少なく静か",
        title: "○○公園の静かな散策路",
        place: { text: "○○県○○市", lat: 35.11, lng: 136.91 },
      },
    ],
    status: {
      label: "Discoveryとつながっています",
      confirmedContext: "○○県○○市 付近 / 秋",
      subject: "夕景",
    },
    confirmationRequests: [],
    discoveryImpacts: [
      {
        discoveryId: "sample-1",
        discoveryTitle: "○○公園の夕焼けスポット",
        discoverySubject: "夕景",
        discoveryPlace: "○○県○○市",
        formationImpacts: ["このDiscoveryの形成に利用されました"],
        providedValues: [{ valueName: "夕焼けが綺麗", impact: "価値向上に利用されました" }],
        reach: { count: 12, period: "今月" },
      },
      {
        discoveryId: "sample-2",
        discoveryTitle: "○○公園の静かな散策路",
        discoverySubject: "散策",
        discoveryPlace: "○○県○○市",
        formationImpacts: [],
        providedValues: [
          { valueName: "人が少なく静か", impact: "形成に利用されました" },
          { valueName: "ベンチが充実", impact: "価値向上に利用されました" },
        ],
      },
      {
        discoveryId: "sample-3",
        discoveryTitle: "○○公園の桜並木",
        discoverySubject: "桜",
        discoveryPlace: "○○県○○市",
        formationImpacts: [],
        providedValues: [],
        valuePending: "この投稿と「○○公園の桜並木」のつながりは確定しています。どの価値に使われたかは現在確認中です。",
      },
      {
        discoveryId: "sample-4",
        discoveryTitle: "○○公園の野鳥観察スポット",
        discoverySubject: "野鳥観察",
        discoveryPlace: "○○県○○市",
        formationImpacts: [],
        providedValues: [{ valueName: "早朝の野鳥が豊富", impact: "価値向上に利用されました" }],
        // Non-blocking #1: 特定済みValueとValue未特定の寄与が併存する例
        valuePending: "この投稿と「○○公園の野鳥観察スポット」のつながりは確定しています。他にどの価値に使われたかは現在確認中です。",
      },
    ],
  },
  "sample-2": {
    id: "sample-2",
    type: "question",
    body: "この石碑、何のためにあるんだろう？由来を知っている人いますか。",
    pictures: [{ caption: "石碑の写真" }],
    place: "△△山 登山道",
    postedAt: "2026-09-19 09:00",
    author: "ユーザーB",
    reactionCount: 3,
    isOwnerViewing: true,
    relatedDiscoveries: [],
    status: {
      label: "Discoveryはまだ決まっていません",
      confirmedContext: "未確定(場所の確認が必要です)",
      subject: "未確定",
    },
    confirmationRequests: ["場所を確認してください", "関連するDiscoveryを選んでください"],
    discoveryImpacts: [],
  },
  "sample-3": {
    id: "sample-3",
    type: "knowledge",
    body: "駅前の広場、朝早い時間は屋台の準備が見られて面白い。",
    pictures: [],
    postedAt: "2026-09-18 07:15",
    author: "ユーザーC",
    reactionCount: 1,
    isOwnerViewing: true,
    relatedDiscoveries: [
      {
        id: "sample-5",
        subject: "朝市",
        value: "早朝の準備風景が見られる",
        title: "駅前広場の朝市",
        place: { text: "○○県○○市", lat: 35.2, lng: 136.8 },
      },
    ],
    status: {
      label: "Discoveryとつながっています",
      confirmedContext: "○○県○○市 付近",
      subject: "朝市",
    },
    confirmationRequests: ["情報を補足してください"],
    discoveryImpacts: [
      {
        discoveryId: "sample-5",
        discoveryTitle: "駅前広場の朝市",
        discoverySubject: "朝市",
        discoveryPlace: "○○県○○市",
        formationImpacts: ["このDiscoveryの形成に利用されました"],
        providedValues: [],
      },
    ],
  },
};

export function getContributionById(id: string): Contribution {
  return CONTRIBUTIONS[id] ?? { ...CONTRIBUTIONS["sample-1"], id };
}
