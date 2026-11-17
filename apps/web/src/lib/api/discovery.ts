// S03(Discovery のページ)が使う API の呼び出し(Issue #112、#135)。
import "server-only";
import { cache } from "react";
import { ApiError, apiClient } from "./client";
import type { components } from "./schema";

export type DiscoveryDetail = components["schemas"]["DiscoveryDetail"];

// 識別子の形が約束に合わない(400)場合も、そのDiscoveryはないものとして扱う
const NOT_FOUND_STATUSES = [400, 404];

/**
 * GET /discoveries/{discoveryId}。ない・見られない場合は undefined。
 * generateMetadata とページで同じ呼び出しを1回にまとめるため、React の cache で包む(リクエストの間だけ)。
 */
export const getDiscovery = cache(async (discoveryId: string): Promise<DiscoveryDetail | undefined> => {
  const { data, response } = await apiClient().GET("/discoveries/{discoveryId}", {
    params: { path: { discoveryId } },
  });
  // openapi-fetch の応答の型は、配列の組(GeoJSON の位置 [経度, 緯度])を number[] に広げるため、
  // スキーマの型(components)に合わせる。形は OpenAPI の約束どおり(API のテストで確かめている)
  if (data) return data as DiscoveryDetail;
  if (NOT_FOUND_STATUSES.includes(response.status)) return undefined;
  throw new ApiError("getDiscovery", response.status);
});
