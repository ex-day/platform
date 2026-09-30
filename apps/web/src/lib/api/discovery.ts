// S03(Discovery詳細)が使う API の呼び出し(Issue #112)。
import "server-only";
import { cache } from "react";
import { ApiError, apiClient } from "./client";
import type { components } from "./schema";

export type DiscoveryDetail = components["schemas"]["DiscoveryDetail"];
export type DiscoveryPosts = components["schemas"]["DiscoveryPosts"];

// 識別子の形が約束に合わない(400)場合も、そのDiscoveryはないものとして扱う
const NOT_FOUND_STATUSES = [400, 404];

/**
 * GET /discoveries/{discoveryId}。ない場合は undefined。
 * generateMetadata とページで同じ呼び出しを1回にまとめるため、React の cache で包む(リクエストの間だけ)。
 */
export const getDiscovery = cache(async (discoveryId: string): Promise<DiscoveryDetail | undefined> => {
  const { data, response } = await apiClient().GET("/discoveries/{discoveryId}", {
    params: { path: { discoveryId } },
  });
  if (data) return data;
  if (NOT_FOUND_STATUSES.includes(response.status)) return undefined;
  throw new ApiError("getDiscovery", response.status);
});

/** GET /discoveries/{discoveryId}/posts。ない場合は undefined */
export async function getDiscoveryPosts(discoveryId: string): Promise<DiscoveryPosts | undefined> {
  const { data, response } = await apiClient().GET("/discoveries/{discoveryId}/posts", {
    params: { path: { discoveryId } },
  });
  if (data) return data;
  if (NOT_FOUND_STATUSES.includes(response.status)) return undefined;
  throw new ApiError("getDiscoveryPosts", response.status);
}
