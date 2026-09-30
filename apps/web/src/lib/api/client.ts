// 画面から API を呼ぶ共通の処理(Issue #112)。
//
// - 型は docs/api/openapi.yaml から openapi-typescript で作る(./schema.d.ts。`npm run gen:api`)。
// - API はサーバー側(Server Component・Route Handler)からだけ呼ぶ。呼び先の URL はブラウザへ公開しない
//   (NEXT_PUBLIC_ を付けない環境変数で渡す)。ブラウザ側からは同じオリジンの中継を通す(./relay.ts、./browser-client.ts。#113)。
// - キャッシュはせず、毎回 API から取る(#89)。
import "server-only";
import createClient from "openapi-fetch";
import type { paths } from "./schema";

const DEFAULT_API_BASE_URL = "http://localhost:8080/api/v1";

/** 呼び出しのたびに作る。環境変数は起動時(実行時)の値を使う */
export function apiClient() {
  return createClient<paths>({
    baseUrl: process.env.EXDAY_API_BASE_URL || DEFAULT_API_BASE_URL,
    cache: "no-store",
  });
}

export class ApiError extends Error {
  constructor(operation: string, status: number) {
    super(`API の呼び出しに失敗しました（${operation}: ${status}）`);
    this.name = "ApiError";
  }
}
