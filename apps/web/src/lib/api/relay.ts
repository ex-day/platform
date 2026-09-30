// ブラウザから API への中継(Issue #113)。
//
// ブラウザは API を直接呼ばず、画面と同じオリジンの Route Handler(src/app/api/...)を通す。
// API の呼び先は、サーバー側の apiClient と同じ環境変数(EXDAY_API_BASE_URL)を実行時に読む。
// 中継するのは、画面が使う API だけ(Route Handler を置いたものだけ)にし、API 全体は公開しない。
import "server-only";

type ApiResult = { body: unknown; status: number; contentType: string | null };

/** API の応答を、状態コードと本文を変えずにブラウザへ返す。API に届かない場合は 502 */
export async function relay(call: () => Promise<ApiResult>): Promise<Response> {
  let result: ApiResult;
  try {
    result = await call();
  } catch (e) {
    console.error("API に届きませんでした", e);
    return Response.json(
      { title: "Bad Gateway", status: 502, detail: "API に届きませんでした" },
      { status: 502, headers: { "content-type": "application/problem+json", "cache-control": "no-store" } },
    );
  }
  const headers = new Headers({ "cache-control": "no-store" });
  if (result.body === undefined) return new Response(null, { status: result.status, headers });
  headers.set("content-type", result.contentType ?? "application/json");
  return new Response(typeof result.body === "string" ? result.body : JSON.stringify(result.body), {
    status: result.status,
    headers,
  });
}
