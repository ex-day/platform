// see docs/ui/screens/S03-discovery-detail.md
//
// S03モック用のダミーデータ。API未接続の段階であり、ここでの型・項目名は
// 実際のAPI契約を先取りするものではない(docs/ui/components/C19-discovery-detail-hero.md
// 「表示に必要な文言・Mediaを用意する担当と具体的なデータ契約は未確定」を参照)。

import type { DraftMedia } from "@/lib/mock-data/media";
import { extractTags } from "@/lib/mock-data/tags";
import { MOCK_SELF_USER_ID } from "@/lib/mock-data/user";

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
  /** 画像のURL(APIのデータで表示する場合)。ない場合はpictureをキャプションとして表示する */
  pictureUrl?: string;
  body: string;
  conditions: DiscoveryCondition[];
};

export type DiscoveryRecommendation = {
  id: string;
  message: string;
  participationMessage?: string;
  /** urlはAPIのデータで表示する場合の画像。ない場合はaltをキャプションとして表示する */
  media?: { alt: string; url?: string };
};

export type DiscoveryPlace = {
  text: string;
  /** APIのPlaceSummaryは座標を返さないため、APIのデータでは持たない */
  lat?: number;
  lng?: number;
};

export type RelatedDiscoverySummary = {
  id: string;
  subject: string;
  value: string;
  title: string;
  picture?: string;
  /** 画像のURL(APIのデータで表示する場合)。ない場合はpictureをキャプションとして表示する */
  pictureUrl?: string;
  place: DiscoveryPlace;
};

/** 派生先Discoveryへの参照(派生の誘導の表示用) */
export type DiscoveryRef = { id: string; title: string };

/**
 * S03の「みんなの声」(C26)に並ぶPost(画面では「声」)。DEC-0008によりContributionから改称。
 * 資料はDiscoveryへ提供されたものとして扱い、どの声とともに提供されたかを示すため声に持たせる。
 * 出典状態は"unset"を許容する(C23)。タグは本文中の「#」で表す(DEC-0009 決定10)。
 */
export type DiscoveryPost = {
  id: string;
  /**
   * 表示名の仮置き(モックデータ用)。本来は声に名前を保存せず、表示のたびに投稿者の今の
   * ニックネームを引いて表示する(Issue #74)。authorIdがモックのログインユーザーの声は、
   * S08(ユーザー情報更新)で変えたニックネームで表示する(C26)。
   */
  author: string;
  /** 投稿者(User)のID。モックではログインユーザー(MOCK_SELF_USER_ID)の声だけに付ける */
  authorId?: string;
  /** 相対表現の表示用文字列(モックのため日時計算はしない) */
  postedAtLabel: string;
  body: string;
  media: DraftMedia[];
  /** 新しい話を始めた最初の声(モック確認用の目印。表示方法は未確定。Issue #53) */
  isOrigin?: boolean;
  /** 返信先の声のID(Post間の参照。PostReference) */
  replyTo?: string;
  /** 派生した話の続きと見立てられた場合の派生先(F02の解析結果。見立てのため再解析で変わり得る) */
  continuedIn?: DiscoveryRef;
  /** 声への共感の数(APIのデータ)。モックでは持たず0とする */
  reactionCount?: number;
};

/** C25。わかってきたこと(Finding)。種類は説と価値(DEC-0009 決定4) */
export type DiscoveryFinding = {
  id: string;
  kind: "value" | "theory";
  text: string;
  /** 資料(出典)の裏付けがある説の出典名。ある場合はリアクションを付けない(DEC-0009 決定9) */
  backedBy?: string;
  /** 種類ごとのリアクション数(価値: understand/wantToGo、裏付けのない説: maybe) */
  reactions: Partial<Record<"understand" | "wantToGo" | "maybe", number>>;
};

/** C26の「派生の目印」。派生が確定した時点の位置(afterPostIdの声の直後)に挟む */
export type DerivationMarker = { afterPostId: string; discovery: DiscoveryRef };

export type Discovery = {
  id: string;
  title: string;
  body: string;
  place: DiscoveryPlace;
  recommendations: DiscoveryRecommendation[];
  otherValues: DiscoveryValueItem[];
  reactionCount: number;
  /** C25。会話から読み取られたわかってきたこと(F02の解析結果のモック) */
  findings: DiscoveryFinding[];
  /** C26。時系列(古い→新しい)順 */
  posts: DiscoveryPost[];
  /** 運営が付けたタグ(#57)。Discoveryのタグは、これと属する声のタグを集めて求める */
  operatorTags?: string[];
  /** Discoveryのタグ(getDiscoveryByIdで求める) */
  tags: string[];
  /** C26の派生の目印 */
  derivationMarkers: DerivationMarker[];
  /** 派生して生まれたDiscoveryの場合の元の会話(C26の冒頭に経緯として示す) */
  derivedOriginNotice?: DiscoveryRef;
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

/** モックの元データ。tagsはgetDiscoveryByIdで求める */
type DiscoveryData = Omit<Discovery, "tags">;

const DISCOVERIES: Record<string, DiscoveryData> = {
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
    findings: [
      { id: "finding-1", kind: "value", text: "夕方、石垣の上から見る夕景が綺麗", reactions: { understand: 18, wantToGo: 25 } },
      { id: "finding-2", kind: "value", text: "春は石垣と桜を一緒に楽しめる", reactions: { understand: 9, wantToGo: 14 } },
      { id: "finding-3", kind: "theory", text: "戦国期には後北条氏の支城だったのでは", reactions: { maybe: 7 } },
      {
        id: "finding-4",
        kind: "theory",
        text: "石垣の積み方は、築かれた時代によって違う",
        backedBy: "○○市郷土資料館 所蔵資料",
        reactions: {},
      },
    ],
    operatorTags: ["城跡"],
    posts: [
      {
        id: "post-1",
        author: "くみ子",
        postedAtLabel: "12日前",
        body: "子どもの頃、祖父とよくここから夕日を見ていました。当時の写真が出てきたので載せます。",
        media: [mockMedia("post-1-media-1", "昭和50年代の城跡.jpg")],
      },
      {
        id: "post-2",
        author: "みつる",
        postedAtLabel: "10日前",
        body: "戦国期には後北条氏の支城だったと聞いたことがあります。",
        media: [],
      },
      {
        id: "post-3",
        author: "夕景さんぽ",
        // モックのログインユーザーの声(S08でニックネームを変えると表示名が変わる。Issue #79)
        authorId: MOCK_SELF_USER_ID,
        postedAtLabel: "6日前",
        body: "市の資料館に古い縄張り図がありました。石垣の積み方も時代で違うみたいです。",
        media: [
          mockMedia("post-3-media-1", "縄張り図.jpg", {
            sourceStatus: "registered",
            sourceType: "document",
            sourceName: "○○市郷土資料館 所蔵資料",
          }),
        ],
      },
      {
        id: "post-4",
        author: "けんじ",
        postedAtLabel: "2日前",
        body: "今週末あたり桜が見頃になりそうです。夕方の石垣と一緒に撮ってきました。 #桜 #夕景",
        media: [mockMedia("post-4-media-1", "夕景と桜.jpg", { sourceStatus: "self" })],
      },
      {
        id: "post-5",
        author: "ゆかり",
        postedAtLabel: "3時間前",
        body: "くみ子さんの写真、今と見比べると石垣の上の木がずいぶん育っていますね。",
        media: [],
        replyTo: "post-1",
      },
      {
        id: "post-6",
        author: "けんじ",
        postedAtLabel: "1時間前",
        body: "夜のライトアップで見る桜もよかったです。堀に映っていました。",
        media: [],
        replyTo: "post-4",
        continuedIn: { id: "derived-to-1", title: "夜桜にまつわる発見 1" },
      },
    ],
    // 桜の話から「夜桜」の話が派生したと確定した時点の位置(モック)
    derivationMarkers: [{ afterPostId: "post-4", discovery: { id: "derived-to-1", title: "夜桜にまつわる発見 1" } }],
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
    // 対立する説は無理に一つにまとめず並べる(DEC-0005 決定9)
    findings: [
      { id: "finding-1", kind: "theory", text: "登山道の分岐を示す道標だったのでは", reactions: { maybe: 4 } },
      { id: "finding-2", kind: "theory", text: "供養塔だったのでは", reactions: { maybe: 3 } },
      { id: "finding-3", kind: "value", text: "石碑の由来を一緒に考える", reactions: { understand: 5, wantToGo: 2 } },
    ],
    operatorTags: ["石碑", "登山道"],
    posts: [
      {
        id: "post-1",
        author: "たけし",
        postedAtLabel: "4日前",
        body: "形からすると道標か供養塔かもしれません。裏側の文字を撮ってきました。",
        media: [mockMedia("post-1-media-1", "石碑の裏側.jpg")],
      },
      {
        id: "post-2",
        author: "はるか",
        postedAtLabel: "1日前",
        body: "年号が読めれば江戸時代のものか分かりそうですね。",
        media: [],
        replyTo: "post-1",
      },
    ],
    derivationMarkers: [],
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
    findings: [],
    posts: [],
    derivationMarkers: [],
    // 派生して生まれたDiscoveryの冒頭の経緯(モック)
    derivedOriginNotice: { id: "derived-from-1", title: "川の上流にまつわる発見 1" },
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

// ---------------------------------------------------------------------------
// 探索段階ごとの確認用モック(Issue #50)
//
// DEC-0005は「探索・議論中と推薦可能な状態を区別する」が、成熟度の段階・遷移条件・
// 内部Entityは後続設計(Issue #36/#46)としている。ここでの「探索開始直後」「探索中」は
// S03の見え方を確認するためのモック上の呼び分けであり、状態の定義を先取りしない。
// 題材はDEC-0005 Contextの例(古写真の問いから青柳干しと分かる)に合わせている。
// ---------------------------------------------------------------------------

export type DiscoveryMockOptions = {
  /** 探索開始直後: 新しい話を始めた最初の声に写真があるか */
  originPhoto?: boolean;
  /** 探索中: 関連Discoveryがあるか */
  withRelated?: boolean;
};

const EXPLORING_TITLE = "鶴見の浜で干していた貝は何？";
const EXPLORING_PLACE: DiscoveryPlace = { text: "神奈川県横浜市鶴見区", lat: 35.5, lng: 139.68 };

function originPost(withPhoto: boolean): DiscoveryPost {
  return {
    id: "post-origin",
    author: "ゆかり",
    postedAtLabel: withPhoto ? "2時間前" : "5日前",
    body: "祖母から、昔は鶴見の浜で貝を干していたと聞きました。何の貝で、どうやって食べていたのか知っている人いますか？ #鶴見 #古写真",
    media: withPhoto ? [mockMedia("post-origin-media-1", "祖母のアルバム_浜の写真.jpg")] : [],
    isOrigin: true,
  };
}

function buildExploringStart(options: DiscoveryMockOptions): DiscoveryData {
  const withPhoto = options.originPhoto ?? false;
  return {
    id: "exploring-start",
    title: EXPLORING_TITLE,
    body: "",
    place: EXPLORING_PLACE,
    // 起点の写真がある場合はそれをHeroに利用し、ない場合はRecommendation 0件として
    // C19の標準Heroを表示する(C19「初期表示(Recommendationが0件の場合)」)
    recommendations: withPhoto
      ? [
          {
            id: "rec-question",
            message: "この浜の貝について疑問が寄せられています",
            participationMessage: "一緒に考えませんか？",
            media: { alt: "起点の投稿の写真：浜で貝を干す人々（年代不明・祖母のアルバム）" },
          },
        ]
      : [],
    otherValues: [],
    reactionCount: 1,
    findings: [],
    posts: [
      originPost(withPhoto),
      {
        id: "post-2",
        author: "みつる",
        postedAtLabel: "1時間前",
        body: "うちの祖父も浜で何か干していたと言っていました。気になります。",
        media: [],
        replyTo: "post-origin",
      },
    ],
    derivationMarkers: [],
    derivedFrom: [],
    derivedTo: [],
    related: [],
  };
}

function buildExploring(options: DiscoveryMockOptions): DiscoveryData {
  return {
    id: "exploring",
    title: EXPLORING_TITLE,
    body: "",
    place: EXPLORING_PLACE,
    recommendations: [
      {
        id: "rec-photo-1",
        message: "昭和の干し場の写真が集まってきています",
        participationMessage: "一緒に考えませんか？",
        media: { alt: "浜に並ぶ干し貝（昭和35年頃・くみ子さん提供）" },
      },
      {
        id: "rec-photo-2",
        message: "今の浜の様子も寄せられています",
        participationMessage: "一緒に考えませんか？",
        media: { alt: "現在の護岸（2026年・けんじさん撮影）" },
      },
    ],
    // 「その他の魅力」まで成立していない状態を確認するため0件とする
    otherValues: [],
    reactionCount: 24,
    findings: [
      {
        id: "finding-1",
        kind: "theory",
        text: "昭和30年代、鶴見の浜では青柳（バカガイ）を干していた",
        backedBy: "鶴見区史",
        reactions: {},
      },
      { id: "finding-2", kind: "theory", text: "時期によってはアサリも干していたのでは", reactions: { maybe: 3 } },
      { id: "finding-3", kind: "value", text: "昭和の浜の暮らしを、古写真でたどれる", reactions: { understand: 11, wantToGo: 4 } },
    ],
    posts: [
      { ...originPost(true), postedAtLabel: "9日前" },
      {
        id: "post-2",
        author: "みつる",
        postedAtLabel: "9日前",
        body: "うちの祖父も浜で何か干していたと言っていました。気になります。",
        media: [],
      },
      {
        id: "post-3",
        author: "くみ子",
        postedAtLabel: "7日前",
        body: "実家に干し場の写真がありました。青柳（バカガイ）を干していたと聞いています。",
        media: [mockMedia("post-3-media-1", "浜の干し場_昭和35年頃.jpg")],
      },
      {
        id: "post-4",
        author: "たけし",
        postedAtLabel: "6日前",
        body: "アサリだったという話も聞いたことがあります。時期によって違ったのかも？",
        media: [],
        replyTo: "post-3",
      },
      {
        id: "post-5",
        author: "はるか",
        postedAtLabel: "4日前",
        body: "区史に「青柳干し」の記述がありました。該当ページを添付します。",
        media: [
          mockMedia("post-5-media-1", "区史_抜粋.pdf", {
            sourceStatus: "registered",
            sourceType: "book",
            sourceName: "鶴見区史",
          }),
        ],
      },
      {
        id: "post-6",
        author: "けんじ",
        postedAtLabel: "2日前",
        body: "今の様子を撮ってきました。干し場の跡はもう残っていないみたいです。",
        media: [mockMedia("post-6-media-1", "現在の護岸.jpg", { sourceStatus: "self" })],
      },
      {
        id: "post-7",
        author: "ゆかり",
        postedAtLabel: "5時間前",
        body: "皆さんありがとうございます。青柳干しの可能性が高そうですね。 #青柳干し",
        media: [],
        replyTo: "post-5",
      },
    ],
    derivationMarkers: [],
    derivedFrom: [],
    derivedTo: [],
    related: options.withRelated
      ? [
          {
            id: "related-aoyagi",
            subject: "青柳干し",
            value: "鶴見で作られていた干物",
            title: "鶴見で作られていた青柳干し",
            place: EXPLORING_PLACE,
          },
        ]
      : [],
  };
}

/** Discoveryのタグは、運営が付けたタグと、属する声のタグを集めて求める(DEC-0009 決定10) */
function withTags(discovery: DiscoveryData): Discovery {
  const tags = new Set(discovery.operatorTags ?? []);
  discovery.posts.forEach((post) => extractTags(post.body).forEach((t) => tags.add(t)));
  return { ...discovery, tags: Array.from(tags) };
}

function resolveDiscovery(id: string, options: DiscoveryMockOptions): DiscoveryData {
  if (id === "exploring-start") return buildExploringStart(options);
  if (id === "exploring") return buildExploring(options);
  return DISCOVERIES[id] ?? { ...DISCOVERIES["sample-1"], id };
}

export function getDiscoveryById(id: string, options: DiscoveryMockOptions = {}): Discovery {
  return withTags(resolveDiscovery(id, options));
}
