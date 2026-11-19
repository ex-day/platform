// S01(TOP)が使う API の呼び出し(Issue #113、#135)。サーバー側(中継の Route Handler)から呼ぶ。
import "server-only";
import { apiClient } from "./client";

/** GET /top。API の応答(状態コードと本文)をそのまま返す */
export async function fetchTop(from: string | null) {
  const { data, error, response } = await apiClient().GET("/top", {
    // 値の検査は API に任せる(形が合わなければ 400、ない場所は 404)
    params: { query: from === null ? undefined : { from } },
  });
  return { body: data ?? error, status: response.status, contentType: response.headers.get("content-type") };
}
