// see docs/ui/screens/S08-user-edit.md, docs/ex-day_logical_entity_design.md(User / UserProfile)
//
// モックのログインユーザー(1人)とプロフィールの初期値。API未接続のため、変更はMockAuthProviderの
// 状態としてのみ保持し、再読み込みで失われる。

/** モックのログインユーザーのID(内部の識別子。画面・URLには出さない。Issue #74) */
export const MOCK_SELF_USER_ID = "mock-self";

/** 興味のある地域(市区町村程度。補完の元データ・粒度は検討事項) */
export type InterestArea = { id: string; name: string };

export type MockProfile = {
  nickname: string;
  interestAreas: InterestArea[];
  /** 興味のある〇〇(項目名・登録の単位はIssue #73の判断待ち。案：保管されているタグと同じ言葉) */
  interests: string[];
};

export const INITIAL_PROFILE: MockProfile = {
  nickname: "夕景さんぽ",
  interestAreas: [
    { id: "yokohama-kohoku", name: "横浜市港北区" },
    { id: "kamakura", name: "鎌倉市" },
    { id: "kyoto-higashiyama", name: "京都市東山区" },
  ],
  interests: ["城跡", "夕景", "古道"],
};

/**
 * 声の表示名。声には名前を保存せず、表示のたびに投稿者の今のニックネームを引く(JOIN。Issue #74)。
 * モックではログインユーザーの声だけをプロフィールから引き、他の人の声は仮置きの名前を使う。
 */
export function resolveAuthorName(
  post: { author: string; authorId?: string },
  profile: MockProfile,
): string {
  return post.authorId === MOCK_SELF_USER_ID ? profile.nickname : post.author;
}
