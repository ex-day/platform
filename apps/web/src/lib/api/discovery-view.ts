// API の型(./schema.d.ts)から、コンポーネントの props への変換(Issue #112、#135)。
//
// コンポーネントは、モックと API の両方のデータで表示するため、API の型に寄せず、
// 今の props の型(@/lib/mock-data/discovery)のまま使う。API の型との違いはここで吸収する。
// S03 は API を DB V2 の形(v0.2)にしたが、画面は作り直す前の最小の対応で、今のコンポーネントに当てはめて出す。
import type { components } from "./schema";
import type {
  DiscoveryFinding,
  DiscoveryRecommendation,
  DiscoveryValueItem,
  RelatedDiscoverySummary,
} from "@/lib/mock-data/discovery";

type Schemas = components["schemas"];

/** S03 の Hero(C19)。v0.2 では Hero の文言を持たないので、Value の説明を出す */
export function toRecommendation(value: Schemas["ValueItem"]): DiscoveryRecommendation {
  return { id: value.id, message: value.description };
}

export function toValueItem(item: Schemas["ValueItem"]): DiscoveryValueItem {
  return {
    id: item.id,
    subject: "",
    value: item.description,
    body: "",
    conditions: item.conditions.map((c) => ({ label: c.label, value: c.text })),
  };
}

/** わかってきたこと(C25)。3つの形(価値・裏付けのない説・裏付けのある説)を、今の props に当てはめる */
export function toFinding(finding: Schemas["FindingItem"]): DiscoveryFinding {
  if (finding.kind === "value") {
    return {
      id: finding.id,
      kind: "value",
      text: finding.text,
      reactions: { understand: finding.reactions.agree, wantToGo: finding.reactions.wantToGo },
    };
  }
  if (finding.backing === "backed") {
    return { id: finding.id, kind: "theory", text: finding.text, backedBy: "資料・出典の裏付けあり", reactions: {} };
  }
  return { id: finding.id, kind: "theory", text: finding.text, reactions: { maybe: finding.reactions.maybe } };
}

/** S03 の関係する Discovery(DiscoverySummary)を、カード(C03)の props にする */
export function toDiscoverySummary(summary: Schemas["DiscoverySummary"]): RelatedDiscoverySummary {
  return {
    id: summary.id,
    title: summary.title,
    subject: "",
    value: summary.summary,
    place: { text: summary.displayPlaces.map((place) => place.name).join("・") },
  };
}

/** 見本の表示(運営の登録を由来に持つ Discovery。どの枠に出ても付ける。S01) */
export const EXAMPLE_LABEL = "ex-day の発見（見本）";

/**
 * S01 の TOP の Discovery のカード(TopDiscoveryCard)を、カード(C03)の props にする。
 * 場所を見出しにする(起点にいちばん近い場所)。value を渡せば、いま・この時期の価値の説明を出す
 */
export function toTopDiscoveryCard(card: Schemas["TopDiscoveryCard"], value?: string): RelatedDiscoverySummary {
  return {
    id: card.discovery.id,
    title: card.discovery.title,
    subject: card.isExample ? EXAMPLE_LABEL : "",
    value: value ?? card.discovery.summary,
    place: { text: card.nearestPlace.name },
  };
}
