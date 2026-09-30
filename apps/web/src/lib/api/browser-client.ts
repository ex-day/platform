// ブラウザ側(Client Component)から API を呼ぶ共通の処理(Issue #113)。
//
// - API を直接は呼ばず、画面と同じオリジンの中継(/api/...。src/lib/api/relay.ts)を通す。
//   中継のパスは API のパス(docs/api/openapi.yaml)の前に /api を付けたものにそろえ、型(./schema.d.ts)をそのまま使う。
// - キャッシュはせず、毎回取る(#89)。
import createClient from "openapi-fetch";
import type { components, paths } from "./schema";

export const RELAY_BASE_PATH = "/api";

export function browserApiClient() {
  return createClient<paths>({ baseUrl: RELAY_BASE_PATH, cache: "no-store" });
}

export type DiscoverySectionList = components["schemas"]["DiscoverySectionList"];
export type DiscoverySectionItems = components["schemas"]["DiscoverySectionItems"];
export type SectionKey = components["schemas"]["SectionKey"];
