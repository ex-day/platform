// GET /api/discovery-sections/{sectionKey}/discoveries → API の同じパスへの中継(Issue #113。src/lib/api/relay.ts)
import type { NextRequest } from "next/server";
import { fetchDiscoverySectionItems } from "@/lib/api/discovery-sections";
import { relay } from "@/lib/api/relay";

export async function GET(request: NextRequest, ctx: RouteContext<"/api/discovery-sections/[sectionKey]/discoveries">) {
  const { sectionKey } = await ctx.params;
  const limit = request.nextUrl.searchParams.get("limit");
  return relay(() => fetchDiscoverySectionItems(sectionKey, limit));
}
