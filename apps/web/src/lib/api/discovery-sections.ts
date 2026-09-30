// S01(Discovery提案)が使う API の呼び出し(Issue #113)。サーバー側(中継の Route Handler)から呼ぶ。
import "server-only";
import { apiClient } from "./client";

/** GET /discovery-sections。API の応答(状態コードと本文)をそのまま返す */
export async function fetchDiscoverySections() {
  const { data, error, response } = await apiClient().GET("/discovery-sections");
  return { body: data ?? error, status: response.status, contentType: response.headers.get("content-type") };
}

/** GET /discovery-sections/{sectionKey}/discoveries。API の応答(状態コードと本文)をそのまま返す */
export async function fetchDiscoverySectionItems(sectionKey: string, limit: string | null) {
  const { data, error, response } = await apiClient().GET("/discovery-sections/{sectionKey}/discoveries", {
    params: {
      // 値の検査は API に任せる(未知の key は 404、不正な limit は 400)
      path: { sectionKey: sectionKey as "nearby" | "season" },
      query: limit === null ? undefined : { limit: limit as unknown as number },
    },
  });
  return { body: data ?? error, status: response.status, contentType: response.headers.get("content-type") };
}
